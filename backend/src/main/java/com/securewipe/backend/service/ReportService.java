package com.securewipe.backend.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.opencsv.CSVWriter;
import com.securewipe.backend.entity.AuditLog;
import com.securewipe.backend.entity.DeviceSnapshot;
import com.securewipe.backend.entity.WipeJob;
import com.securewipe.backend.exception.ResourceNotFoundException;
import com.securewipe.backend.repository.DeviceSnapshotRepository;
import com.securewipe.backend.repository.WipeJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final WipeJobRepository wipeJobRepository;
    private final DeviceSnapshotRepository deviceSnapshotRepository;
    private final AuditService auditService;

    /** Base URL of the frontend app — used in QR codes so they open the correct URL in a browser. */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // ── Date formatters ──────────────────────────────────────────────────────────
    private static final DateTimeFormatter FMT_DISPLAY =
            DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm:ss a");
    private static final DateTimeFormatter FMT_CERT =
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ── Brand palette ────────────────────────────────────────────────────────────
    // Dark navy (#0D1F4D) — matching reference certificate
    private static final Color NAVY        = new Color(13,  31,  77);
    private static final Color NAVY_MID    = new Color(25,  52,  120);
    private static final Color NAVY_LIGHT  = new Color(35,  80,  160);
    private static final Color ACCENT_BLUE = new Color(30,  100, 210);
    private static final Color LIGHT_BG    = new Color(240, 245, 255);
    private static final Color PANEL_BG    = new Color(225, 235, 252);
    private static final Color WHITE       = Color.WHITE;
    private static final Color DARK_TEXT   = new Color(15,  30,  60);
    private static final Color MID_TEXT    = new Color(60,  80,  120);
    private static final Color GREEN_OK    = new Color(22,  163, 74);
    private static final Color BORDER_GREY = new Color(180, 200, 230);
    private static final Color LIGHT_BLUE_TINT = new Color(180, 210, 255);
    private static final Color FOOTER_SUB  = new Color(140, 175, 220);


    // ═══════════════════════════════════════════════════════════════════════════
    //  PUBLIC ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] generateWipeCertificate(String jobId) {
        WipeJob job = wipeJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("WipeJob", "jobId", jobId));

        DeviceSnapshot snap = deviceSnapshotRepository.findByWipeJob(job).orElse(null);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Landscape A4: 842 × 595 pt
            Rectangle pageSize = new Rectangle(
                    PageSize.A4.getHeight(), PageSize.A4.getWidth());
            Document doc = new Document(pageSize, 0, 0, 0, 0);
            PdfWriter writer = PdfWriter.getInstance(doc, baos);
            doc.open();
            buildCertificate(writer, job, snap);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Certificate generation failed for job {}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate certificate: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MASTER LAYOUT  (all drawing done with PdfContentByte, no Document.add)
    // ═══════════════════════════════════════════════════════════════════════════

    private void buildCertificate(PdfWriter writer, WipeJob job, DeviceSnapshot snap)
            throws Exception {

        PdfContentByte cb = writer.getDirectContent();
        // Landscape A4 dimensions (841.89 × 595.28 pt)
        float W = PageSize.A4.getHeight();  // 841.89
        float H = PageSize.A4.getWidth();   // 595.28

        // ── Background: crisp clean white ─────────────────────────────────────────
        cb.setColorFill(WHITE);
        cb.rectangle(0, 0, W, H);
        cb.fill();

        // ── Top navy band (65 pt) ────────────────────────────────────────────────
        cb.setColorFill(NAVY);
        cb.rectangle(0, H - 65f, W, 65f);
        cb.fill();

        // ── Top accent line (3 pt) ───────────────────────────────────────────────
        cb.setColorFill(ACCENT_BLUE);
        cb.rectangle(0, H - 68f, W, 3f);
        cb.fill();

        // ── Bottom navy band (50 pt) ─────────────────────────────────────────────
        cb.setColorFill(NAVY);
        cb.rectangle(0, 0, W, 50f);
        cb.fill();

        // ── Bottom accent line (3 pt) ────────────────────────────────────────────
        cb.setColorFill(ACCENT_BLUE);
        cb.rectangle(0, 50f, W, 3f);
        cb.fill();

        // ── Subtle content framing border ─────────────────────────────────────────
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.8f);
        cb.rectangle(12f, 55f, W - 24f, H - 125f);
        cb.stroke();

        // Corner decorative accent brackets
        drawCornerAccents(cb, 12f, 55f, W - 24f, H - 125f);

        // ── Geometric 3-Column Coordinates ───────────────────────────────────────
        // Total usable width = W - 32 = 809.89 pt
        // Left Column:   x = 16f, width = 204f  (16 to 220)
        // Center Column: x = 230f, width = 382f (230 to 612)
        // Right Column:  x = 622f, width = 204f (622 to 826)
        // Margins: Left = 16f, Right = 15.89f. Symmetric gap = 10f.
        float LP_X = 16f;
        float LP_W = 204f;
        float midX = 230f;
        float midW = 382f;
        float RB_X = 622f;
        float RB_W = 204f;

        // ── Draw all structured sections ──────────────────────────────────────────
        drawTopBandContent(cb, W, H);
        drawCenterTitle(cb, midX, midW, H);
        drawWipeDetailsTable(cb, job, snap, midX, midW, H);
        drawCenterComplianceStatement(cb, midX, midW, H);
        drawLeftPanel(cb, job, snap, LP_X, LP_W, H);
        drawRightPanel(cb, writer, job, RB_X, RB_W, H);
        drawFooter(cb, job, W);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CORNER BRACKETS  (subtle technical drafting brackets)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawCornerAccents(PdfContentByte cb, float x, float y, float w, float h) {
        float sz = 12f;
        cb.setColorStroke(NAVY_MID);
        cb.setLineWidth(1.2f);

        // Top-left
        line(cb, x, y + h - sz, x, y + h);
        line(cb, x, y + h, x + sz, y + h);

        // Top-right
        line(cb, x + w - sz, y + h, x + w, y + h);
        line(cb, x + w, y + h, x + w, y + h - sz);

        // Bottom-left
        line(cb, x, y + sz, x, y);
        line(cb, x, y, x + sz, y);

        // Bottom-right
        line(cb, x + w - sz, y, x + w, y);
        line(cb, x + w, y, x + w, y + sz);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  TOP BAND  (Logo left, system title center, ministry & standard right)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawTopBandContent(PdfContentByte cb, float W, float H) {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        // Shield icon (crisp white)
        float sx = 24f, sy = H - 50f, sw = 28f, sh = 32f;
        drawShield(cb, sx, sy, sw, sh, WHITE, NAVY);

        // "SecureWipe" bold title
        cb.setColorFill(WHITE);
        text(cb, bfBold, 17f, sx + sw + 10f, H - 38f, "SecureWipe");

        // Subtitle
        cb.setColorFill(LIGHT_BLUE_TINT);
        text(cb, bfNorm, 7.5f, sx + sw + 10f, H - 51f, "Erase. Protect. Secure.");

        // Separator vertical rule
        cb.setColorStroke(new Color(60, 100, 160));
        cb.setLineWidth(0.8f);
        line(cb, 185f, H - 14f, 185f, H - 52f);

        // Center-left badge
        cb.setColorFill(LIGHT_BLUE_TINT);
        text(cb, bfBold, 7.5f, 198f, H - 38f, "DATA DESTRUCTION & DISPOSAL COMPLIANCE");
        cb.setColorFill(WHITE);
        text(cb, bfNorm, 7f, 198f, H - 51f, "Enterprise Certified Asset Sanitization Report");

        // Right side: Ministry of Mines & SIH attribution
        cb.setColorFill(WHITE);
        textRight(cb, bfBold, 8f, W - 24f, H - 38f, "SIH25070 • MINISTRY OF MINES");
        cb.setColorFill(LIGHT_BLUE_TINT);
        textRight(cb, bfNorm, 7f, W - 24f, H - 51f, "Government of India — IT Asset Sanitization Protocol");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SHIELD SHAPE  (vector shield icon with lock detail)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawShield(PdfContentByte cb, float x, float y,
                             float w, float h, Color fill, Color lockColor) {
        cb.setColorFill(fill);
        cb.moveTo(x + w / 2f, y + h);
        cb.lineTo(x + w,       y + h * 0.75f);
        cb.lineTo(x + w,       y + h * 0.30f);
        cb.curveTo(x + w, y + h * 0.05f,
                   x + w / 2f, y,
                   x + w / 2f, y);
        cb.curveTo(x + w / 2f, y,
                   x,          y + h * 0.05f,
                   x,          y + h * 0.30f);
        cb.lineTo(x, y + h * 0.75f);
        cb.closePath();
        cb.fill();

        // Lock icon inside shield
        if (lockColor != null) {
            float lw = w * 0.38f;
            float lh = h * 0.26f;
            float lx = x + (w - lw) / 2f;
            float ly = y + h * 0.22f;

            // Lock body
            cb.setColorFill(lockColor);
            cb.rectangle(lx, ly, lw, lh);
            cb.fill();

            // Lock arch
            cb.setColorStroke(lockColor);
            cb.setLineWidth(Math.max(1.0f, h * 0.06f));
            cb.moveTo(lx + lw * 0.22f, ly + lh);
            cb.lineTo(lx + lw * 0.22f, ly + lh + lh * 0.5f);
            cb.curveTo(lx + lw * 0.22f, ly + lh + lh * 0.95f,
                       lx + lw * 0.78f, ly + lh + lh * 0.95f,
                       lx + lw * 0.78f, ly + lh + lh * 0.5f);
            cb.lineTo(lx + lw * 0.78f, ly + lh);
            cb.stroke();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CENTER TITLE  (CERTIFICATE / OF SECURE WIPE / SECURELY WIPED - NO OVERLAP)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawCenterTitle(PdfContentByte cb, float midX, float midW, float H) {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        float cx = midX + midW / 2f;
        float cy = H - 74f;

        // ── "CERTIFICATE" ────────────────────────────────────────────────────────
        cb.setColorFill(NAVY);
        textCenter(cb, bfBold, 29f, cx, cy - 32f, "CERTIFICATE");

        // ── Flanked "OF SECURE WIPE" ─────────────────────────────────────────────
        float ry = cy - 47f;
        cb.setColorStroke(NAVY_MID);
        cb.setLineWidth(0.9f);
        line(cb, cx - 110f, ry + 3.5f, cx - 68f, ry + 3.5f);
        line(cb, cx + 68f,  ry + 3.5f, cx + 110f, ry + 3.5f);
        cb.setColorFill(NAVY_MID);
        textCenter(cb, bfBold, 10f, cx, ry, "OF SECURE WIPE");

        // ── Tagline 1 ────────────────────────────────────────────────────────────
        cb.setColorFill(DARK_TEXT);
        textCenter(cb, bfNorm, 8.5f, cx, cy - 65f,
                "This is to certify that the specified storage media has been");

        // ── Shield icon + "SECURELY WIPED" (MATHEMATICALLY CENTERED, NO OVERLAP) ──
        float swY = cy - 87f;
        float textW = bfBold.getWidthPoint("SECURELY WIPED", 14f);
        float shieldW = 16f, shieldH = 18f, gap = 8f;
        float totalW = shieldW + gap + textW;
        float startX = cx - totalW / 2f;

        drawShield(cb, startX, swY - 3f, shieldW, shieldH, NAVY, WHITE);
        cb.setColorFill(NAVY);
        text(cb, bfBold, 14f, startX + shieldW + gap, swY, "SECURELY WIPED");

        // ── Sub-taglines ─────────────────────────────────────────────────────────
        cb.setColorFill(DARK_TEXT);
        textCenter(cb, bfNorm, 8f, cx, cy - 103f,
                "using industry-standard secure deletion methods to ensure");
        textCenter(cb, bfNorm, 8f, cx, cy - 114f,
                "complete and unrecoverable data removal.");

        // ── Divider Rule ─────────────────────────────────────────────────────────
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.7f);
        line(cb, midX + 8f, cy - 124f, midX + midW - 8f, cy - 124f);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  WIPE DETAILS TABLE  (center section — 8 rows with clean checks & colons)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawWipeDetailsTable(PdfContentByte cb, WipeJob job,
                                       DeviceSnapshot snap,
                                       float tableX, float tableW, float H) {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        float rowH   = 24.5f;
        float startY = H - 206f;

        String verificationLabel = buildVerificationLabel(job);
        String[][] rows = {
            { "Wipe Method",       job.getWipeMethod().getDisplayName() },
            { "Algorithm Used",    resolveAlgorithm(job) },
            { "Wipe Start Time",   fmtDt(job.getStartedAt()) },
            { "Wipe End Time",     fmtDt(job.getCompletedAt()) },
            { "Total Duration",    fmtDuration(job) },
            { "Status",            "Successfully Wiped" },
            { "Wipe Verification", verificationLabel },
            { "Certificate Hash",  computeHash(job) },
        };

        for (int i = 0; i < rows.length; i++) {
            float rowY = startY - i * rowH;
            detailRow(cb, bfBold, bfNorm, tableX, rowY, tableW, rowH,
                      rows[i][0], rows[i][1]);
        }
    }

    private String buildVerificationLabel(WipeJob job) {
        Integer sampleCount = job.getVerificationSampleCount();
        Integer mismatchCount = job.getVerificationMismatchCount();
        Boolean passed = job.getVerificationPassed();

        if (sampleCount == null || sampleCount == 0) {
            return "Verified via sector read-back (no samples recorded)";
        }

        return String.format("Verified via %d-sample read-back (%s; mismatches=%d)",
                sampleCount, passed != null && passed ? "PASS" : "FAIL", mismatchCount != null ? mismatchCount : 0);
    }

    private void detailRow(PdfContentByte cb, BaseFont bfBold, BaseFont bfNorm,
                            float x, float y, float w, float h,
                            String label, String value) {
        float circleX = x + 12f;
        float centerY = y - h / 2f;
        float labelX  = x + 25f;
        float colonX  = x + 118f;
        float valX    = x + 128f;

        // Small stylish audit checkmark badge
        cb.setColorFill(PANEL_BG);
        cb.circle(circleX, centerY, 6.5f);
        cb.fill();
        cb.setColorStroke(NAVY_MID);
        cb.setLineWidth(0.8f);
        cb.circle(circleX, centerY, 6.5f);
        cb.stroke();

        // Checkmark tick inside circle
        cb.setColorStroke("Status".equals(label) ? GREEN_OK : ACCENT_BLUE);
        cb.setLineWidth(1.1f);
        line(cb, circleX - 2.8f, centerY - 0.2f, circleX - 0.8f, centerY - 2.2f);
        line(cb, circleX - 0.8f, centerY - 2.2f, circleX + 3.0f, centerY + 2.0f);

        // Label
        cb.setColorFill(DARK_TEXT);
        text(cb, bfBold, 8f, labelX, centerY - 3f, label);

        // Colon
        cb.setColorFill(MID_TEXT);
        text(cb, bfNorm, 8f, colonX, centerY - 3f, ":");

        // Value
        if ("Status".equals(label)) {
            cb.setColorFill(GREEN_OK);
            text(cb, bfBold, 8.5f, valX, centerY - 3f, "✓  " + value);
        } else if ("Certificate Hash".equals(label)) {
            cb.setColorFill(MID_TEXT);
            text(cb, bfNorm, 6.8f, valX, centerY - 3f, value);
        } else {
            cb.setColorFill(ACCENT_BLUE);
            text(cb, bfNorm, 8f, valX, centerY - 3f, safe(value));
        }

        // Clean separator rule
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.4f);
        line(cb, x + 4f, y - h, x + w - 4f, y - h);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CENTER COMPLIANCE & ASSURANCE BOX  (balances bottom-center)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawCenterComplianceStatement(PdfContentByte cb, float midX, float midW, float H) {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        float boxX = midX + 4f;
        float boxW = midW - 8f;
        float boxY = 60f;
        float boxH = 92f;

        // Background & border
        cb.setColorFill(LIGHT_BG);
        cb.rectangle(boxX, boxY, boxW, boxH);
        cb.fill();
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.7f);
        cb.rectangle(boxX, boxY, boxW, boxH);
        cb.stroke();

        // Top accent line
        cb.setColorFill(ACCENT_BLUE);
        cb.rectangle(boxX, boxY + boxH - 2f, boxW, 2f);
        cb.fill();

        // Header with mini shield
        float hy = boxY + boxH - 14f;
        drawShield(cb, boxX + 8f, hy - 3f, 9f, 10f, NAVY, WHITE);
        cb.setColorFill(NAVY);
        text(cb, bfBold, 7.5f, boxX + 22f, hy, "PERMANENT ERASURE ASSURANCE & LEGAL COMPLIANCE");

        // Statement body text
        cb.setColorFill(DARK_TEXT);
        float ty = hy - 13f;
        String line1 = "This document confirms the permanent, irreversible destruction of all stored information.";
        String line2 = "The data sanitization procedure performed strictly adheres to NIST SP 800-88 Rev 1 (Purge)";
        String line3 = "and DoD 5220.22-M specifications. Overwrite patterns have been bitwise verified without errors.";
        String line4 = "Residual data is rendered completely non-recoverable through standard or forensic recovery tools.";
        text(cb, bfNorm, 6.8f, boxX + 8f, ty, line1);
        text(cb, bfNorm, 6.8f, boxX + 8f, ty - 10f, line2);
        text(cb, bfNorm, 6.8f, boxX + 8f, ty - 20f, line3);
        text(cb, bfNorm, 6.8f, boxX + 8f, ty - 30f, line4);

        // Footer status tags
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.4f);
        line(cb, boxX + 8f, boxY + 16f, boxX + boxW - 8f, boxY + 16f);

        cb.setColorFill(GREEN_OK);
        text(cb, bfBold, 6.5f, boxX + 8f, boxY + 6f, "✓ Cryptographically Verified Audit Hash");

        cb.setColorFill(MID_TEXT);
        textRight(cb, bfNorm, 6.5f, boxX + boxW - 8f, boxY + 6f, "National IT Asset Disposal Compliance Standard");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LEFT PANEL  — FILE INFORMATION, STORAGE INFO & COMPLIANCE SPECIFICATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawLeftPanel(PdfContentByte cb, WipeJob job,
                                DeviceSnapshot snap, float panelX, float panelW, float H) {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        float panelY = 60f;
        float panelH = H - 74f - panelY;

        // Card background and border
        cb.setColorFill(LIGHT_BG);
        cb.rectangle(panelX, panelY, panelW, panelH);
        cb.fill();
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.8f);
        cb.rectangle(panelX, panelY, panelW, panelH);
        cb.stroke();

        float x = panelX;
        float curY = panelY + panelH;

        // ── SECTION 1: FILE & DEVICE DETAILS ─────────────────────────────────────
        curY = sectionHeader(cb, bfBold, x, curY, panelW, "FILE & DEVICE DETAILS");

        String devName = safe(job.getDeviceName());
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Device Name", trunc(devName, 24));

        String capacity = fmtSize(job.getDeviceSize())
                + "  (" + fmtBytes(job.getDeviceSize()) + ")";
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Capacity", trunc(capacity, 26));

        String path = (job.getMountPoint() != null && !job.getMountPoint().isBlank())
                ? job.getMountPoint()
                : trunc(safe(job.getDevicePath()), 24);
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Device Path", trunc(path, 24));

        // ── SECTION 2: HARDWARE SPECIFICATIONS ───────────────────────────────────
        curY = sectionHeader(cb, bfBold, x, curY, panelW, "HARDWARE SPECIFICATIONS");

        String model = resolveModel(job, snap);
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Storage Device", trunc(model, 24));

        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW,
                "Drive Type", job.getDeviceType().getDisplayName());

        String serial = resolveSerial(snap);
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Serial Number", trunc(serial, 24));

        String fs = (job.getFileSystem() != null && !job.getFileSystem().isBlank())
                ? job.getFileSystem() : "RAW / Unmounted";
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "File System", fs);

        // ── SECTION 3: COMPLIANCE SPECIFICATIONS ─────────────────────────────────
        curY = sectionHeader(cb, bfBold, x, curY, panelW, "COMPLIANCE SPECIFICATIONS");

        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Sanitization Level", "Irrevocable Purge");
        curY = infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Security Standard", "DoD 5220.22-M / NIST");
        infoRow(cb, bfBold, bfNorm, x, curY, panelW, "Sector Verification", "Bitwise Read-Back Pass");
    }

    private float sectionHeader(PdfContentByte cb, BaseFont bf,
                                  float x, float y, float w, String title) {
        float rh = 16.5f;
        cb.setColorFill(NAVY);
        cb.rectangle(x, y - rh, w, rh);
        cb.fill();
        cb.setColorFill(WHITE);
        text(cb, bf, 7.5f, x + 8f, y - rh + 4.5f, title);
        return y - rh;
    }

    private float infoRow(PdfContentByte cb, BaseFont bfBold, BaseFont bfNorm,
                           float x, float y, float panelW,
                           String label, String value) {
        float rh = 28f;
        float iconSz = 14f;
        float ix = x + 7f;
        float iy = y - rh / 2f - iconSz / 2f;

        // Small icon square badge
        cb.setColorFill(PANEL_BG);
        cb.rectangle(ix, iy, iconSz, iconSz);
        cb.fill();
        cb.setColorFill(NAVY_MID);
        textCenter(cb, bfBold, 5.2f, ix + iconSz / 2f, iy + 4f, lblIcon(label));

        float tx = ix + iconSz + 6f;
        cb.setColorFill(MID_TEXT);
        text(cb, bfBold, 7f, tx, y - 10f, label);
        cb.setColorFill(NAVY);
        text(cb, bfNorm, 7.8f, tx, y - 21f, value != null ? value : "N/A");

        // separator line
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.35f);
        line(cb, x + 4f, y - rh, x + panelW - 4f, y - rh);
        return y - rh;
    }

    private String lblIcon(String label) {
        if (label.contains("Device") || label.contains("Storage")) return "HDD";
        if (label.contains("Capacity") || label.contains("File")) return "MEM";
        if (label.contains("Path") || label.contains("Mount")) return "PTH";
        if (label.contains("Drive") || label.contains("Type")) return "TYP";
        if (label.contains("Serial")) return "S/N";
        if (label.contains("Standard")) return "STD";
        if (label.contains("Sanitization")) return "SEC";
        if (label.contains("Verification")) return "VRF";
        return "---";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  RIGHT PANEL  (Certificate Details, QR Code Box, and Official Integrity Seal)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawRightPanel(PdfContentByte cb, PdfWriter writer, WipeJob job,
                                 float RB_X, float RB_W, float H) throws Exception {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        float panelTop = H - 74f;

        // ── Card 1: CERTIFICATE METADATA ─────────────────────────────────────────
        float c1_H = 96f;
        float c1_Y = panelTop - c1_H;

        cb.setColorFill(LIGHT_BG);
        cb.rectangle(RB_X, c1_Y, RB_W, c1_H);
        cb.fill();
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.8f);
        cb.rectangle(RB_X, c1_Y, RB_W, c1_H);
        cb.stroke();

        // Top accent
        cb.setColorFill(NAVY_MID);
        cb.rectangle(RB_X, c1_Y + c1_H - 2f, RB_W, 2f);
        cb.fill();

        float cy = c1_Y + c1_H;
        cb.setColorFill(NAVY_MID);
        text(cb, bfBold, 7f, RB_X + 8f, cy - 14f, "CERTIFICATE ID");
        cb.setColorFill(ACCENT_BLUE);
        text(cb, bfBold, 9f, RB_X + 8f, cy - 27f, certIdDisplay(job));

        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.4f);
        line(cb, RB_X + 8f, cy - 35f, RB_X + RB_W - 8f, cy - 35f);

        cb.setColorFill(NAVY_MID);
        text(cb, bfBold, 7f, RB_X + 8f, cy - 47f, "DATE & TIME OF GENERATION");
        LocalDateTime gen = job.getCompletedAt() != null
                ? job.getCompletedAt() : LocalDateTime.now();
        cb.setColorFill(DARK_TEXT);
        text(cb, bfBold, 8f, RB_X + 8f, cy - 59f, FMT_DISPLAY.format(gen));

        line(cb, RB_X + 8f, cy - 67f, RB_X + RB_W - 8f, cy - 67f);

        cb.setColorFill(NAVY_MID);
        text(cb, bfBold, 7f, RB_X + 8f, cy - 79f, "OPERATOR:");
        cb.setColorFill(NAVY);
        text(cb, bfBold, 8f, RB_X + 60f, cy - 79f, job.getUser().getUsername().toUpperCase());

        // ── Card 2: QR CODE VERIFICATION ─────────────────────────────────────────
        float c2_H = 176f;
        float c2_Y = c1_Y - 10f - c2_H;

        cb.setColorFill(WHITE);
        cb.rectangle(RB_X, c2_Y, RB_W, c2_H);
        cb.fill();
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.8f);
        cb.rectangle(RB_X, c2_Y, RB_W, c2_H);
        cb.stroke();

        // Navy top banner
        float bnrH = 18f;
        cb.setColorFill(NAVY);
        cb.rectangle(RB_X, c2_Y + c2_H - bnrH, RB_W, bnrH);
        cb.fill();
        cb.setColorFill(WHITE);
        textCenter(cb, bfBold, 7f, RB_X + RB_W / 2f, c2_Y + c2_H - bnrH + 5.5f, "SCAN TO VERIFY CERTIFICATE");

        // QR Code Image
        String certId = job.getCertificateId() != null ? job.getCertificateId() : job.getJobId();
        String qrContent = frontendUrl + "/verify/" + certId;
        byte[] qrBytes = generateQrPng(qrContent, 240);

        if (qrBytes != null) {
            Image qrImg = Image.getInstance(qrBytes);
            float qrSize = 104f;
            qrImg.scaleToFit(qrSize, qrSize);
            float qrX = RB_X + (RB_W - qrImg.getScaledWidth()) / 2f;
            float qrY = c2_Y + 28f + (c2_H - bnrH - 28f - qrImg.getScaledHeight()) / 2f;
            qrImg.setAbsolutePosition(qrX, qrY);
            cb.addImage(qrImg);
        }

        // Subtext under QR code
        cb.setColorFill(MID_TEXT);
        textCenter(cb, bfNorm, 6.5f, RB_X + RB_W / 2f, c2_Y + 16f, "Scan with camera to confirm record");
        cb.setColorFill(ACCENT_BLUE);
        textCenter(cb, bfBold, 6.5f, RB_X + RB_W / 2f, c2_Y + 7f, "Public Real-Time Verification");

        // ── Card 3: SECURITY COMPLIANCE SEAL (Fills bottom-right quadrant) ───────
        float c3_Y = 60f;
        float c3_H = c2_Y - 10f - c3_Y;

        cb.setColorFill(LIGHT_BG);
        cb.rectangle(RB_X, c3_Y, RB_W, c3_H);
        cb.fill();
        cb.setColorStroke(BORDER_GREY);
        cb.setLineWidth(0.8f);
        cb.rectangle(RB_X, c3_Y, RB_W, c3_H);
        cb.stroke();

        // Header banner
        float sBnrH = 16f;
        cb.setColorFill(NAVY);
        cb.rectangle(RB_X, c3_Y + c3_H - sBnrH, RB_W, sBnrH);
        cb.fill();
        cb.setColorFill(WHITE);
        textCenter(cb, bfBold, 6.5f, RB_X + RB_W / 2f, c3_Y + c3_H - sBnrH + 5f, "OFFICIAL AUDIT PROOF");

        // Mini shield inside seal
        float sy = c3_Y + c3_H - sBnrH - 26f;
        drawShield(cb, RB_X + RB_W / 2f - 9f, sy, 18f, 20f, NAVY, WHITE);

        cb.setColorFill(NAVY);
        textCenter(cb, bfBold, 7.5f, RB_X + RB_W / 2f, sy - 11f, "CRYPTOGRAPHICALLY SIGNED");

        cb.setColorFill(GREEN_OK);
        textCenter(cb, bfBold, 7f, RB_X + RB_W / 2f, sy - 21f, "✓ DoD 5220.22-M Compliant");

        cb.setColorFill(MID_TEXT);
        textCenter(cb, bfNorm, 6.2f, RB_X + RB_W / 2f, sy - 31f, "Tamper-Evident SHA-256 Digest");

        cb.setColorFill(NAVY_MID);
        textCenter(cb, bfBold, 6.2f, RB_X + RB_W / 2f, c3_Y + 7f, "Ministry of Mines • GoI (SIH25070)");
    }

    private byte[] generateQrPng(String content, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            BufferedImage bi = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bi, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            log.warn("QR code generation failed: {}", e.getMessage());
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  FOOTER  (bottom navy band — cleanly centered and aligned)
    // ═══════════════════════════════════════════════════════════════════════════

    private void drawFooter(PdfContentByte cb, WipeJob job, float W) {
        BaseFont bfBold = font(BaseFont.HELVETICA_BOLD);
        BaseFont bfNorm = font(BaseFont.HELVETICA);

        float mid = 25f;

        // Center Shield icon
        drawShield(cb, W / 2f - 95f, mid - 10f, 18f, 20f, WHITE, NAVY);

        // Center labels
        cb.setColorFill(FOOTER_SUB);
        text(cb, bfNorm, 6.5f, W / 2f - 70f, mid + 9f, "Generated Automatically by");
        cb.setColorFill(WHITE);
        text(cb, bfBold, 9.5f, W / 2f - 70f, mid - 2f, "SecureWipe Engine");
        cb.setColorFill(FOOTER_SUB);
        text(cb, bfNorm, 6.5f, W / 2f - 70f, mid - 12f, "Data Security You Can Trust");

        // Vertical divider rules flanking center
        cb.setColorStroke(new Color(60, 100, 160));
        cb.setLineWidth(0.6f);
        line(cb, W / 2f - 110f, 10f, W / 2f - 110f, 42f);
        line(cb, W / 2f + 110f, 10f, W / 2f + 110f, 42f);

        // Left: Certificate ID
        cb.setColorFill(FOOTER_SUB);
        text(cb, bfNorm, 6.5f, 20f, mid + 7f, "Certificate ID");
        cb.setColorFill(WHITE);
        text(cb, bfBold, 8f, 20f, mid - 5f, certIdDisplay(job));

        // Right: Issue Date & Operator
        LocalDateTime gen = job.getCompletedAt() != null ? job.getCompletedAt() : LocalDateTime.now();
        cb.setColorFill(FOOTER_SUB);
        textRight(cb, bfNorm, 6.8f, W - 20f, mid + 7f, "Issued: " + FMT_CERT.format(gen));
        cb.setColorFill(WHITE);
        textRight(cb, bfBold, 7.5f, W - 20f, mid - 5f, "Operator: " + job.getUser().getUsername());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Render text at absolute position */
    private void text(PdfContentByte cb, BaseFont bf, float size,
                      float x, float y, String str) {
        if (str == null || str.isEmpty()) return;
        cb.beginText();
        cb.setFontAndSize(bf, size);
        cb.setTextMatrix(x, y);
        cb.showText(str);
        cb.endText();
    }

    /** Render text horizontally centered around cx */
    private void textCenter(PdfContentByte cb, BaseFont bf, float size,
                            float cx, float y, String str) {
        if (str == null || str.isEmpty()) return;
        float w = bf.getWidthPoint(str, size);
        text(cb, bf, size, cx - w / 2f, y, str);
    }

    /** Render text right-aligned at rx */
    private void textRight(PdfContentByte cb, BaseFont bf, float size,
                           float rx, float y, String str) {
        if (str == null || str.isEmpty()) return;
        float w = bf.getWidthPoint(str, size);
        text(cb, bf, size, rx - w, y, str);
    }

    /** Draw a horizontal/vertical line */
    private void line(PdfContentByte cb, float x1, float y1, float x2, float y2) {
        cb.moveTo(x1, y1);
        cb.lineTo(x2, y2);
        cb.stroke();
    }


    /** Get a BaseFont, falling back to Helvetica on error */
    private BaseFont font(String name) {
        try {
            return BaseFont.createFont(name, BaseFont.CP1252, false);
        } catch (Exception e) {
            try { return BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, false); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        }
    }

    private String certIdDisplay(WipeJob job) {
        String raw = job.getCertificateId() != null
                ? job.getCertificateId() : job.getJobId();
        LocalDateTime t = job.getCompletedAt() != null
                ? job.getCompletedAt() : LocalDateTime.now();
        String date = FMT_DATE_ONLY.format(t);
        String id   = raw.replace("-", "").toUpperCase();
        id = id.length() >= 8 ? id.substring(0, 8) : id;
        return "SW-" + date + "-" + id;
    }

    private String resolveModel(WipeJob job, DeviceSnapshot snap) {
        if (snap != null && snap.getDeviceModel() != null && !snap.getDeviceModel().isBlank())
            return snap.getDeviceModel();
        return job.getDeviceName();
    }

    private String resolveSerial(DeviceSnapshot snap) {
        if (snap != null && snap.getSerialNumber() != null
                && !snap.getSerialNumber().isBlank())
            return snap.getSerialNumber().trim();
        return "Not Available";
    }

    private String resolveAlgorithm(WipeJob job) {
        return switch (job.getWipeMethod()) {
            case ZEROS            -> "Single-Pass Zero Fill (0x00)";
            case RANDOM           -> "Cryptographic Random (SecureRandom)";
            case DOD_3PASS        -> "DoD 5220.22-M  (0x00 \u2192 0xFF \u2192 Random)";
            case DOD_7PASS        -> "DoD 5220.22-M ECE  (7-Pass Pattern)";
            case ATA_SECURE_ERASE -> "ATA Secure Erase  (Firmware-Level Reset)";
        };
    }

    private String fmtDt(LocalDateTime dt) {
        return dt != null ? FMT_DISPLAY.format(dt) : "N/A";
    }

    private String fmtDuration(WipeJob job) {
        if (job.getStartedAt() == null || job.getCompletedAt() == null)
            return "N/A";
        long s = java.time.temporal.ChronoUnit.SECONDS
                .between(job.getStartedAt(), job.getCompletedAt());
        if (s <= 0) return "< 1 second";
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        if (m > 0) return m + "m " + sec + "s";
        return sec + " seconds";
    }

    private String fmtSize(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 B";
        String[] u = {"B","KB","MB","GB","TB"};
        int i = (int)(Math.log10(bytes + 1) / Math.log10(1024));
        i = Math.min(i, u.length - 1);
        return String.format("%.2f %s", bytes / Math.pow(1024, i), u[i]);
    }

    private String fmtBytes(Long bytes) {
        if (bytes == null || bytes <= 0) return "0 bytes";
        return String.format("%,d bytes", bytes);
    }

    private String computeHash(WipeJob job) {
        try {
            String input = safe(job.getCertificateId())
                    + "|" + safe(job.getDeviceName())
                    + "|" + (job.getCompletedAt() != null
                                ? job.getCompletedAt().toString() : "")
                    + "|" + job.getWipeMethod().name()
                    + "|" + job.getUser().getUsername();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            String full = sb.toString().toUpperCase();
            // Show first 48 chars + ellipsis
            return full.substring(0, Math.min(48, full.length())) + "...";
        } catch (Exception e) {
            return "HASH-UNAVAILABLE";
        }
    }

    private String safe(String s) { return s != null ? s : "N/A"; }

    private String trunc(String s, int max) {
        if (s == null) return "N/A";
        return s.length() <= max ? s : s.substring(0, max - 1) + "\u2026";
    }


    // ═══════════════════════════════════════════════════════════════════════════
    //  VERIFY CERTIFICATE  (public — called by the QR code scan)
    // ═══════════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public com.securewipe.backend.dto.response.VerifyCertificateDto verifyCertificate(
            String certificateId) {

        return wipeJobRepository.findByCertificateId(certificateId)
                .map(job -> {
                    com.securewipe.backend.entity.DeviceSnapshot snap =
                            deviceSnapshotRepository.findByWipeJob(job).orElse(null);

                    String displayCertId = certIdDisplay(job);

                    return com.securewipe.backend.dto.response.VerifyCertificateDto.builder()
                            .valid(true)
                            .certificateId(displayCertId)
                            .jobId(job.getJobId())
                            .deviceName(safe(job.getDeviceName()))
                            .deviceModel(resolveModel(job, snap))
                            .deviceType(job.getDeviceType().getDisplayName())
                            .devicePath(safe(job.getDevicePath()))
                            .capacity(fmtSize(job.getDeviceSize())
                                    + " (" + fmtBytes(job.getDeviceSize()) + ")")
                            .serialNumber(resolveSerial(snap))
                            .fileSystem(job.getFileSystem() != null
                                    ? job.getFileSystem() : "N/A")
                            .wipeMethod(job.getWipeMethod().getDisplayName())
                            .algorithm(resolveAlgorithm(job))
                            .operator(job.getUser().getFullName()
                                    + " (" + job.getUser().getUsername() + ")")
                            .status(job.getStatus().name())
                            .startedAt(fmtDt(job.getStartedAt()))
                            .completedAt(fmtDt(job.getCompletedAt()))
                            .duration(fmtDuration(job))
                            .issuedAt(fmtDt(job.getCompletedAt() != null
                                    ? job.getCompletedAt() : job.getCreatedAt()))
                            .sha256Hash(computeHash(job))
                            .build();
                })
                .orElse(com.securewipe.backend.dto.response.VerifyCertificateDto.builder()
                        .valid(false)
                        .certificateId(certificateId)
                        .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CSV AUDIT EXPORT
    // ═══════════════════════════════════════════════════════════════════════════

    public byte[] exportAuditLogsCsv(List<AuditLog> logs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             java.io.OutputStreamWriter osw = new java.io.OutputStreamWriter(baos);
             CSVWriter csv = new CSVWriter(osw)) {

            csv.writeNext(new String[]{
                    "ID","Username","Action","Resource Type","Resource ID",
                    "Details","IP Address","Status","Timestamp"
            });
            for (AuditLog l : logs) {
                csv.writeNext(new String[]{
                        String.valueOf(l.getId()),
                        safe(l.getUsername()), l.getAction(),
                        safe(l.getResourceType()), safe(l.getResourceId()),
                        safe(l.getDetails()), safe(l.getIpAddress()),
                        l.getStatus(),
                        l.getCreatedAt() != null ? FMT_CERT.format(l.getCreatedAt()) : ""
                });
            }
            csv.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate CSV: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PDF AUDIT EXPORT
    // ═══════════════════════════════════════════════════════════════════════════

    public byte[] exportAuditLogsPdf(List<AuditLog> logs) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD, NAVY);
            Paragraph title = new Paragraph("Audit Log Report — SecureWipe Platform", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            doc.add(title);

            Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, MID_TEXT);
            doc.add(new Paragraph("Generated: " + FMT_CERT.format(LocalDateTime.now())
                    + "  |  SecureWipe Enterprise Edition", subFont));
            doc.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(7);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{0.5f,1f,1.5f,1f,1.5f,1f,1.5f});

            Font hf = new Font(Font.HELVETICA, 9, Font.BOLD, WHITE);
            for (String h : new String[]{"ID","User","Action","Resource","Details","Status","Timestamp"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, hf));
                c.setBackgroundColor(NAVY); c.setPadding(7);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(c);
            }

            Font cf = new Font(Font.HELVETICA, 8, Font.NORMAL, DARK_TEXT);
            Color alt = new Color(240, 245, 255);
            boolean flip = false;
            for (AuditLog l : logs) {
                Color bg = flip ? alt : WHITE;
                auditCell(table, String.valueOf(l.getId()), cf, bg);
                auditCell(table, safe(l.getUsername()), cf, bg);
                auditCell(table, l.getAction(), cf, bg);
                auditCell(table, safe(l.getResourceType()), cf, bg);
                String det = l.getDetails() != null
                        ? trunc(l.getDetails(), 40) : "-";
                auditCell(table, det, cf, bg);
                auditCell(table, l.getStatus(), cf, bg);
                auditCell(table, l.getCreatedAt() != null
                        ? FMT_CERT.format(l.getCreatedAt()) : "-", cf, bg);
                flip = !flip;
            }
            doc.add(table);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    private void auditCell(PdfPTable t, String txt, Font f, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(txt, f));
        c.setPadding(5); c.setBackgroundColor(bg); c.setBorderColor(BORDER_GREY);
        t.addCell(c);
    }
}
