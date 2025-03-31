package com.kitcha.board.service;

import com.kitcha.board.entity.Board;
import com.kitcha.board.entity.File;
import com.kitcha.board.repository.BoardRepository;
import com.kitcha.board.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class FileService {
    @Autowired
    private BoardRepository boardRepository;
    @Autowired
    private FileRepository fileRepository;

    // S3Client 빈 주입
    @Autowired
    private S3Client s3Client;

    // 실제 사용중인 S3 버킷 이름으로 변경하세요.
    @Value("${spring.cloud.aws.s3.region}")
    private String bucketName;

    @Async("taskExecutor")
    public void createPdf(Board board) throws IOException {
        // PDF 문서 생성
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);

        // 폰트 로드
        InputStream titleFontStream = getClass().getResourceAsStream("/fonts/NotoSansKR-ExtraBold.ttf");
        PDType0Font titleFont = PDType0Font.load(document, titleFontStream);
        InputStream contentFontStream = getClass().getResourceAsStream("/fonts/NotoSansKR-Regular.ttf");
        PDType0Font contentFont = PDType0Font.load(document, contentFontStream);

        // 배너 이미지 로드
        InputStream inputStream = getClass().getResourceAsStream("/images/Background.png");
        PDImageXObject image = PDImageXObject.createFromByteArray(document, inputStream.readAllBytes(), "Background.png");

        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();

        // 내용 처리
        String content = board.getLongSummary();
        String[] lines = content.split("\n");
        float yPosition = 650;

        try {
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.drawImage(image, 0, 0, pageWidth, pageHeight);

            // 제목 추가
            contentStream.beginText();
            contentStream.setFont(titleFont, 18);
            contentStream.newLineAtOffset(50, pageHeight - 200);
            float maxWidth = pageWidth - 100;
            List<String> wrappedLines = wrapText(board.getNewsTitle(), titleFont, 18, maxWidth);
            for (String line : wrappedLines) {
                contentStream.showText(line);
                contentStream.newLineAtOffset(0, -26);
            }
            contentStream.endText();

            // 내용 추가
            float contentStartY = pageHeight - 200 - (wrappedLines.size() * 26) - 20;
            contentStream.beginText();
            contentStream.setFont(contentFont, 12);
            contentStream.newLineAtOffset(50, contentStartY);
            wrappedLines = wrapText(board.getLongSummary(), contentFont, 12, maxWidth);
            for (String line : wrappedLines) {
                contentStream.showText(line);
                contentStream.newLineAtOffset(0, -20);
            }
            contentStream.endText();
            contentStream.close();

            /* 기존 로컬에 파일을 저장하던 로직을 S3로 변경 */
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            document.close();
            byte[] pdfBytes = out.toByteArray();

            String fileName = board.getNewsTitle().replaceAll("\\s+", "_") + ".pdf";
            String s3Key = "pdfs/" + fileName;

            // S3 업로드 요청
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType("application/pdf")
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(pdfBytes));
            log.info("Uploaded PDF to S3 with key: {}", s3Key);

            // DB에 S3 키 저장
            File file = new File(board.getBoardId(), board.getNewsTitle(), s3Key);
            fileRepository.save(file);

        } catch (IOException e) {
            e.printStackTrace();
            log.error("FileService.createPdf() : PDF 파일 생성 오류");
        }
    }

    // S3에서 파일을 다운로드하는 새 메서드
    public DownloadedFile downloadPdf(Long boardId) throws IOException {
        Optional<Board> boardOptional = boardRepository.findById(boardId);
        if (boardOptional.isEmpty() || boardOptional.get().isDeletedYn()) {
            log.error("Board not found or is deleted for boardId: {}", boardId);
            return null;
        }
        Optional<File> fileOptional = fileRepository.findById(boardId);
        if (fileOptional.isEmpty()) {
            log.error("File not found for boardId: {}", boardId);
            return null;
        }
        File file = fileOptional.get();
        String s3Key = file.getFilePath();
        log.info("Attempting to download file from S3 with key: {}", s3Key);
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();
            ResponseInputStream<GetObjectResponse> s3Object = s3Client.getObject(getRequest);
            log.info("S3 object retrieved successfully. Response metadata: {}", s3Object.response());
            byte[] content = s3Object.readAllBytes();
            return new DownloadedFile(file.getFileName(), content);
        } catch (Exception e) {
            log.error("Error retrieving S3 object with key {}: {}", s3Key, e.getMessage());
            throw e;
        }
    }

    // PDF 파일 다운로드 결과를 담기 위한 DTO
    public static class DownloadedFile {
        private String fileName;
        private byte[] content;

        public DownloadedFile(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getContent() {
            return content;
        }
    }

    // 줄바꿈 계산 함수 (기존 코드 그대로)
    public List<String> wrapText(String text, PDFont font, int fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\n")) {
            StringBuilder currentLine = new StringBuilder();
            float currentWidth = 0;
            for (String word : paragraph.split(" ")) {
                float wordWidth = font.getStringWidth(word) / 1000 * fontSize;
                if (currentWidth + wordWidth > maxWidth) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                    currentWidth = wordWidth;
                } else {
                    if (currentLine.length() > 0) {
                        currentLine.append(" ");
                        currentWidth += font.getStringWidth(" ") / 1000 * fontSize;
                    }
                    currentLine.append(word);
                    currentWidth += wordWidth;
                }
            }
            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }
}
