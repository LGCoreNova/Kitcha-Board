package com.kitcha.board.controller;

import com.kitcha.board.dto.BoardCreate;
import com.kitcha.board.dto.BoardDetail;
import com.kitcha.board.dto.BoardList;
import com.kitcha.board.dto.BoardUpdate;
import com.kitcha.board.entity.Board;
import com.kitcha.board.entity.File;
import com.kitcha.board.service.BoardService;
import com.kitcha.board.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/apps/board")
public class BoardController {
    @Autowired
    private BoardService boardService;
    @Autowired
    private FileService fileService;

    // 1. 게시글 작성
    @PostMapping
    public ResponseEntity<Object> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Nickname") String nickname,
            @RequestBody BoardCreate boardCreate) throws IOException {

        Board newBoard = boardService.create(Long.parseLong(userId), nickname, boardCreate);

        return (newBoard != null) ?
                ResponseEntity.ok().body(newBoard) :
                ResponseEntity.badRequest().build();
    }

    // 2. 목록 조회
    @GetMapping
    public ResponseEntity<List<BoardList>> list(@RequestParam int page, @RequestParam int size) {
        List<BoardList> results = boardService.list(page, size);

        return ResponseEntity.ok().body(results);
    }

    // 3. 상세 조회
    @GetMapping("/{boardId}")
    public ResponseEntity<BoardDetail> detail(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long boardId) {

        BoardDetail boardDetail = boardService.detail(Long.parseLong(userId), role, boardId);

        return (boardDetail != null) ?
                ResponseEntity.ok().body(boardDetail) :
                ResponseEntity.badRequest().build();
    }

    // 4. 수정
    @PutMapping("/{boardId}")
    public void update(
            @PathVariable Long boardId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody BoardUpdate boardUpdate) {

        boardService.update(boardId, Long.parseLong(userId), boardUpdate);
    }

    // 5. 삭제
    @DeleteMapping("/{boardId}")
    public void delete(
            @PathVariable Long boardId,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {

        boardService.delete(boardId, Long.parseLong(userId), role);
    }

    // 6. 첨부파일 다운로드
//    @GetMapping("/{boardId}/download")
//    public void download(@PathVariable Long boardId,
//                         HttpServletResponse response) throws IOException {
//        Optional<File> optional = fileService.download(boardId);
//
//        if (optional.isEmpty()) {
//            return;
//        }
//
//        File file = optional.get();
//
//        Path path = Paths.get(file.getFilePath());
//        byte[] result = Files.readAllBytes(path);
//
//        response.setContentType("application/octet-stream");
//        response.setContentLength(result.length);
//        response.setHeader("Content-Disposition",
//                "attachment; fileName=\"" + URLEncoder.encode(file.getFileName() + ".pdf", "UTF-8") + "\";");
//        response.setHeader("Content-Transfer-Encoding", "binary");
//        response.getOutputStream().write(result);
//        response.getOutputStream().flush();
//        response.getOutputStream().close();
//    }

    // 6. 첨부파일 다운로드 - S3에서 PDF 파일 읽어오기
    @GetMapping("/{boardId}/download")
    public void download(@PathVariable Long boardId,
                         HttpServletResponse response) throws IOException {
        FileService.DownloadedFile downloadedFile = fileService.downloadPdf(boardId);
        if (downloadedFile == null) {
            return;
        }
        byte[] result = downloadedFile.getContent();

        response.setContentType("application/octet-stream");
        response.setContentLength(result.length);
        response.setHeader("Content-Disposition",
                "attachment; fileName=\"" + URLEncoder.encode(downloadedFile.getFileName() + ".pdf", "UTF-8") + "\";");
        response.setHeader("Content-Transfer-Encoding", "binary");
        response.getOutputStream().write(result);
        response.getOutputStream().flush();
        response.getOutputStream().close();
    }

}
