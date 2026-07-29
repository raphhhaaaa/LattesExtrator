package com.uem.extrator.service;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
    private static DownloadManager instance;
    private Map<String, FileInfo> fileMap;

    private DownloadManager() {
        fileMap = new ConcurrentHashMap<>();
        iniciarLimpezaAutomatica();
    }

    private void iniciarLimpezaAutomatica() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // Roda a cada 1 hora varrendo a memória e o disco
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("[DownloadManager] Iniciando varredura do Zelador Automático...");
            long agora = System.currentTimeMillis();
            for (Map.Entry<String, FileInfo> entry : fileMap.entrySet()) {
                if (agora - entry.getValue().getCreatedAt() > 86400000) { // 24 horas
                    try {
                        entry.getValue().getFile().delete();
                        fileMap.remove(entry.getKey());
                        logger.info("[DownloadManager] Limpeza de arquivo expirado concluída para o token: {}", entry.getKey());
                    } catch (Exception e) {
                        logger.error("[DownloadManager] Erro ao deletar arquivo físico.", e);
                    }
                }
            }
        }, 1, 1, TimeUnit.HOURS);
    }

    public static synchronized DownloadManager getInstance() {
        if (instance == null) {
            instance = new DownloadManager();
        }
        return instance;
    }

    public String registerFile(File file) {
        String token = UUID.randomUUID().toString();
        fileMap.put(token, new FileInfo(file, System.currentTimeMillis()));
        logger.info("[DownloadManager] Arquivo registrado para download. Token: {}", token);
        return token;
    }

    public File getFile(String token) {
        FileInfo info = fileMap.get(token);
        if (info == null) {
            return null;
        }
        
        // Verifica expiracao de 24 horas (86400000 ms)
        if (System.currentTimeMillis() - info.getCreatedAt() > 86400000) {
            logger.warn("[DownloadManager] Token expirado (mais de 24h): {}", token);
            info.getFile().delete();
            fileMap.remove(token);
            return null;
        }
        
        return info.getFile();
    }
    
    public void unregisterFile(String token) {
        fileMap.remove(token);
    }

    private static class FileInfo {
        private File file;
        private long createdAt;

        public FileInfo(File file, long createdAt) {
            this.file = file;
            this.createdAt = createdAt;
        }

        public File getFile() { return file; }
        public long getCreatedAt() { return createdAt; }
    }
}
