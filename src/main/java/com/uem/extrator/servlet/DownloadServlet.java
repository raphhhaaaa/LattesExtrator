package com.uem.extrator.servlet;

import com.uem.extrator.service.DownloadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

@WebServlet("/api/download")
public class DownloadServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(DownloadServlet.class);

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getParameter("token");
        if (token == null || token.trim().isEmpty()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Token não informado.");
            return;
        }

        File file = DownloadManager.getInstance().getFile(token);
        
        if (file == null || !file.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Arquivo não encontrado ou link de download expirado.");
            return;
        }

        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"exportacao_lattes.zip\"");
        resp.setContentLengthLong(file.length());

        try (FileInputStream in = new FileInputStream(file);
             OutputStream out = resp.getOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            logger.info("[DownloadServlet] Download concluído com sucesso para o token {}", token);
        } catch (IOException e) {
            logger.error("[DownloadServlet] Erro durante streaming do download", e);
        }
    }
}
