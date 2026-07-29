package com.uem.extrator.viewmodel;

import com.uem.extrator.dao.*;
import com.uem.extrator.model.*;
import com.uem.extrator.service.SemanticScholarService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zk.ui.event.EventListener;
import java.util.Set;
import java.util.HashSet;
import com.uem.extrator.service.LattesService;
import org.zkoss.zul.Window;
import com.uem.extrator.model.Usuario;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListagemVM {

    private Usuario usuarioLogado;

    // logger instancia
    private static final Logger logger = LoggerFactory.getLogger(ListagemVM.class);

    // Services
    private LattesService lattesService = new LattesService();

    // DAOs
    private CurriculoDAO curriculoDAO = new CurriculoDAO();
    private CursoDAO cursoDAO = new CursoDAO();
    private InstituicaoDAO instituicaoDAO = new InstituicaoDAO();
    private ProducaoDAO producaoDAO = new ProducaoDAO();

    // Pessoas/Curriculos
//    private List<Curriculo> listaCurriculosCompleta = new ArrayList<>(); // cache para nao ir ao banco toda vez
//    private List<Curriculo> listaCurriculos = new ArrayList<>(); // lista que aparece na tela
    private List<Curriculo> listaCurriculos = new ArrayList<>(); // apenas os (tamanhoPagina) da tela atual
    private int tamanhoPagina = 21;
    private int paginaAtual = 0;
    private long totalCurriculos = 0;

    // Cursos
    private List<Curso> listaCursos = new ArrayList<>();
    private List<Curso> listaCursosCompleta = new ArrayList<>(); // cache

    // Instituições
    private List<Object[]> listaInstituicoes = new ArrayList<>();
    private List<Object[]> listainstituicoesCompleta = new ArrayList<>(); // cache

    // utilitarios
    private Curriculo curriculo;
    private boolean resumoExpandido = false;

    // --- PESQUISA E FILTROS --- //
    private String termoPesquisaCurriculo = "";
    private String termoPesquisaCurso = "";
    private String termoPesquisaInstituicao = "";

    // --- EXPORTACAO XML --- //
    private boolean modoExportacao = false;
    private Set<Curriculo> curriculosSelecionados = new HashSet<>();

    @Init
    public void init() {
        // Usuario
        this.usuarioLogado = (Usuario) Sessions.getCurrent().getAttribute("usuario_logado");
        carregarListas();
    }

    @Command
    @NotifyChange({"listaCurriculos", "listaCursos"})
    public void atualizar() {
        carregarListas();
    }

    private void carregarListas() {
        // carrega tudo do banco
        this.listainstituicoesCompleta = instituicaoDAO.listarInstituicoesConsolidadas();
        this.listaInstituicoes = new ArrayList<>(this.listainstituicoesCompleta);

        this.listaCursosCompleta = cursoDAO.listarTodos();
        this.listaCursos = new ArrayList<>(this.listaCursosCompleta);

        carregarCurriculoPaginado();

    }

    private void carregarCurriculoPaginado() {
        int offset = this.paginaAtual * this.tamanhoPagina;
        this.totalCurriculos = curriculoDAO.contarTotalPaginado(this.termoPesquisaCurriculo);
        this.listaCurriculos = curriculoDAO.listarPaginado(offset, this.tamanhoPagina, this.termoPesquisaCurriculo);
    }

    @Command
    @NotifyChange("listaCursos")
    public void pesquisarCurso() {
        String texto = this.termoPesquisaCurso;

        if (texto == null || texto.trim().isEmpty()) {
            this.listaCursos = new ArrayList<>(this.listaCursosCompleta);
        } else {
            String termo = texto.toLowerCase();
            this.listaCursos.clear();

            for (Curso c: this.listaCursosCompleta) {

                boolean matchNome = c.getNomeCurso() != null && c.getNomeCurso().toLowerCase().contains(termo);

                if (matchNome) {
                    this.listaCursos.add(c);
                }
            }
        }
    }

    @Command
    @NotifyChange("listaInstituicoes")
    public void pesquisarInstituicao() {
        String texto = this.termoPesquisaInstituicao;

        if (texto == null || texto.trim().isEmpty()) {
            this.listaInstituicoes = new ArrayList<>(this.listainstituicoesCompleta);
        } else {
            String termo = texto.toLowerCase();
            this.listaInstituicoes.clear();

            for (Object[] row : this.listainstituicoesCompleta) {
                String nomeInstituicao = (String) row[0];

                if (nomeInstituicao != null && nomeInstituicao.toLowerCase().contains(termo)) {
                    this.listaInstituicoes.add(row);
                }
            }
        }
    }

    @Command
    @NotifyChange("listaCurriculos")
    public void pesquisar() {
        this.paginaAtual = 0; // volta para a primeira pagina ao pesquisar
        carregarCurriculoPaginado();
    }

    @Command
    @NotifyChange("listaCurriculos")
    public void mudarPagina() {
        carregarCurriculoPaginado();
    }

    @Command
    @NotifyChange({"modoExportacao", "curriculosSelecionados"})
    public void ativarModoExportacao() {
        this.modoExportacao = true;
        this.curriculosSelecionados.clear();
    }

    @Command
    @NotifyChange({"modoExportacao", "curriculosSelecionados"})
    public void desativarModoExportacao() {
        this.modoExportacao = false;
        this.curriculosSelecionados.clear();
    }

    @Command
    @NotifyChange("curriculosSelecionados")
    public void toggleSelecao(@BindingParam("item") Curriculo item, @BindingParam("checked") boolean checked) {
        if (checked) {
            this.curriculosSelecionados.add(item);
        } else {
            this.curriculosSelecionados.remove(item);
        }
    }

    @Command
    @NotifyChange("curriculosSelecionados")
    public void selecionarTodos() {
        this.curriculosSelecionados.addAll(this.listaCurriculos);
    }

    @Command
    @NotifyChange("curriculosSelecionados")
    public void desmarcarTodos() {
        this.curriculosSelecionados.clear();
    }

    @Command
    public void downloadXmls() {
        if (this.curriculosSelecionados.isEmpty()) {
            Clients.showNotification("Selecione pelo menos um currículo.", "warning", null, null, 3000);
            return;
        }

        try {
            LattesService lattesService = new LattesService();
            com.uem.extrator.service.ILattesSOAP port = lattesService.criarCliente();

            if (this.curriculosSelecionados.size() == 1) {
                Curriculo c = this.curriculosSelecionados.iterator().next();
                byte[] zipBytes = port.getCurriculoCompactado(c.getIdLattes());
                if (zipBytes != null && zipBytes.length > 0) {
                    String xml = lattesService.descompactarZip(zipBytes);
                    org.zkoss.zul.Filedownload.save(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/xml", c.getIdLattes() + ".xml");
                }
            } else {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos);

                for (Curriculo c : this.curriculosSelecionados) {
                    try {
                        byte[] zipBytes = port.getCurriculoCompactado(c.getIdLattes());
                        if (zipBytes != null && zipBytes.length > 0) {
                            String xml = lattesService.descompactarZip(zipBytes);
                            zos.putNextEntry(new java.util.zip.ZipEntry(c.getIdLattes() + ".xml"));
                            zos.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            zos.closeEntry();
                        }
                    } catch (Exception ex) {
                        logger.error("Falha ao baixar ID " + c.getIdLattes(), ex);
                    }
                }
                zos.close();
                org.zkoss.zul.Filedownload.save(baos.toByteArray(), "application/zip", "exportacao_lattes_selecionados.zip");
            }
        } catch (Exception e) {
            logger.error("Erro na exportação ZIP", e);
            Clients.showNotification("Erro ao processar o download.", "error", null, null, 3000);
        }
    }

    @Command
    public void downloadTodaBaseBackground() {
        // Cálculo de estimativa: ~0.2 segundos por currículo (baseado em benchmark de 8244 em 27 min)
        long tempoEstimadoSegundos = (long) (this.totalCurriculos * 0.2);
        long minutos = tempoEstimadoSegundos / 60;
        String textoTempo = minutos > 0 ? (minutos + " minuto(s)") : "alguns segundos";

        String msg = "Exportação massiva iniciada! Tempo estimado: ~" + textoTempo + ". Você receberá um e-mail ao concluir.";
        logger.info("[Exportação] Solicitada exportação massiva para {} currículos. Tempo estimado: {}", this.totalCurriculos, textoTempo);

        Clients.showNotification(msg, "info", null, null, 6000);
        
        final String baseUrl = org.zkoss.zk.ui.Executions.getCurrent().getScheme() + "://" +
                         org.zkoss.zk.ui.Executions.getCurrent().getServerName() + ":" +
                         org.zkoss.zk.ui.Executions.getCurrent().getServerPort() +
                         org.zkoss.zk.ui.Executions.getCurrent().getContextPath();
                         
        new Thread(() -> {
            try {
                logger.info("Iniciando exportação COMPLETA em background...");
                java.util.List<Curriculo> todos = curriculoDAO.listarTodos();
                LattesService lattesService = new LattesService();
                com.uem.extrator.service.ILattesSOAP port = lattesService.criarCliente();

                java.io.File tempFile = java.io.File.createTempFile("exportacao_lattes_toda_base_", ".zip");
                try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(tempFile))) {
                    for (Curriculo c : todos) {
                        try {
                            byte[] zipBytes = port.getCurriculoCompactado(c.getIdLattes());
                            if (zipBytes != null && zipBytes.length > 0) {
                                String xml = lattesService.descompactarZip(zipBytes);
                                zos.putNextEntry(new java.util.zip.ZipEntry(c.getIdLattes() + ".xml"));
                                zos.write(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                                zos.closeEntry();
                            }
                        } catch (Exception ex) {
                            logger.error("Falha ao processar batch ID " + c.getIdLattes(), ex);
                        }
                    }
                }
                logger.info("Exportação em background finalizada. Arquivo: " + tempFile.getAbsolutePath());
                
                long lengthBytes = tempFile.length();
                if (lengthBytes < (25 * 1024 * 1024)) { // Menor que 25MB
                    com.uem.extrator.service.EmailService.getInstance().enviarComAnexo(
                        "Exportação Lattes Concluída", 
                        "A extração massiva de toda a base Lattes terminou com sucesso.<br><br>O arquivo compactado contendo todos os currículos em formato XML (" + (lengthBytes / 1024) + " KB) segue em anexo direto neste e-mail.",
                        tempFile
                    );
                    tempFile.delete(); // Deleta imediatamente pois já foi enviado como anexo
                    logger.info("Arquivo enviado como anexo e deletado do servidor.");
                } else {
                    // Maior que 25MB, usa o servlet
                    String token = com.uem.extrator.service.DownloadManager.getInstance().registerFile(tempFile);
                    String downloadUrl = baseUrl + "/api/download?token=" + token;
                    
                    com.uem.extrator.service.EmailService.getInstance().enviarAlerta(
                        "Exportação Lattes Concluída (Arquivo Grande)", 
                        "A extração massiva de toda a base Lattes terminou com sucesso.<br><br>"
                        + "O arquivo compactado contendo todos os currículos em formato XML está pronto.<br><br>"
                        + "<b>Tamanho do Arquivo:</b> " + (lengthBytes / (1024 * 1024)) + " MB<br><br>"
                        + "<a href='" + downloadUrl + "' style='padding: 10px 15px; background-color: #007bff; color: white; text-decoration: none; border-radius: 5px;'>Baixar Arquivo ZIP</a>"
                        + "<br><br><small>Nota: Este link expirará automaticamente em 24 horas.</small>"
                    );
                }

            } catch (Exception e) {
                logger.error("Erro na thread de exportação full.", e);
            }
        }).start();
    }

    @Command
    public void verDetalhes(@BindingParam("item") Curriculo item) {
        this.curriculo = curriculoDAO.buscarComDetalhes(item.getIdLattes());
        this.resumoExpandido = false;

        if (this.curriculo != null) {
            Map<String, Object> args = new HashMap<>();
            args.put("vm", this);
            Window win = (Window) Executions.createComponents("/paginas/modalDetalhes.zul", null, args);

            // Dispara o carregamento automático
            iniciarCarregamentoAutomatico();

            win.doModal();
        } else {
            org.zkoss.zk.ui.util.Clients.showNotification("Erro ao carregar detalhes", "error", null, null, 2000);
        }
    }

    private void iniciarCarregamentoAutomatico() {
        if (this.curriculo == null || this.curriculo.getProducoes() == null) return;

        // Captura o Desktop atual para poder voltar depois
        final Desktop desktop = Executions.getCurrent().getDesktop();
        if (!desktop.isServerPushEnabled()) {
            desktop.enableServerPush(true);
        }

        new Thread(() -> {
            logger.debug(">>> [THREAD] Iniciando varredura UI...");
            boolean houveAtualizacao = false;

            for (Producao artigo : this.curriculo.getProducoes()) {
                if (artigo.getDoi() == null || artigo.getDoi().trim().isEmpty()) continue;

                // verifica se precisa buscar nas APIs
                if (precisaAtualizar(artigo)) {

                    // 1. marca visualmente que está buscando
                    atualizarInterface(desktop, artigo, null, "...", "secondary");

                    try {
                        SemanticScholarService semanticScholarService = new SemanticScholarService();

                        Object[] metricas = semanticScholarService.buscarMetricaUnicas(artigo.getDoi());
                        Integer cits = (Integer) metricas[0];
                        String[] acesso = (String[]) metricas[1];

                        // 3. atualiza Objeto e salva no banco
                        artigo.setCitacoes(cits);
                        artigo.setStatusAcesso(acesso[0]);
                        artigo.setDataAtualizacaoMetricas(new Date()); // marca data do dia atual

                        // persistência
                        producaoDAO.atualizarMetricas(artigo);

                        // 4. atualiza tela
                        atualizarInterface(desktop, artigo, cits, acesso[0], artigo.getCorAcesso());
                        houveAtualizacao = true;

                        // pausa de segurança para a API
                        Thread.sleep(100);
                    } catch (Exception e) {
                        logger.error("Erro buscar métricas bibliométricas", e);
                    }
                }
                // Se não precisa atualizar (já tem no banco e é recente), não faz nada.
            }

            // 5. Refresh Final de Segurança (Garante que tudo apareça)
            if (houveAtualizacao && desktop.isAlive()) {
                Executions.schedule(desktop, new EventListener<Event>() {
                    public void onEvent(Event event) {
                        // Força o redesenho da lista inteira
                        BindUtils.postNotifyChange(null, null, curriculo, "producoes");
                        logger.debug(">>> [UI] Refresh Final Executado.");
                    }
                }, new Event("refreshAll"));
            }

        }).start();
    }

    // regra de negócio: atualiza se estiver vazio OU se tiver mais de 7 dias.
    private boolean precisaAtualizar(Producao p) {
        if (p.getCitacoes() == null) return true;
        if (p.getDataAtualizacaoMetricas() == null) return true;

        long diff = new Date().getTime() - p.getDataAtualizacaoMetricas().getTime();
        long dias = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);

        return dias > 7; // se faz mais de 7 dias, atuaiza.
    }

    // Metodo seguro para atualizar a tela vindo de outra Thread
    private void atualizarInterface(Desktop desktop, Producao artigo, Integer citacoes, String acesso, String cor) {
        if (!desktop.isAlive()) return;

        Executions.schedule(desktop, new EventListener<Event>() {
            public void onEvent(Event event) {
                // Atualiza os valores no objeto
                artigo.setCitacoes(citacoes);
                artigo.setStatusAcesso(acesso);
                artigo.setCorAcesso(cor);

                // NOTIFICAÇÃO AGRESSIVA:
                // 1. Notifica propriedades específicas
                BindUtils.postNotifyChange(null, null, artigo, "citacoes");
                BindUtils.postNotifyChange(null, null, artigo, "acesso");
                BindUtils.postNotifyChange(null, null, artigo, "corAcesso");

                // 2. Notifica o objeto inteiro (Força bruta para o ZK acordar)
                BindUtils.postNotifyChange(null, null, artigo, "*");
            }
        }, new Event("updateItem"));
    }

    @Command
    @NotifyChange("resumoExpandido")
    public void toggleResumo() {
        this.resumoExpandido = !this.resumoExpandido;
    }

    @Command
    public void fecharResultado() {}

    // Getters e Setters
    public List<Curriculo> getListaCurriculos() { return listaCurriculos; }
    public List<Curso> getListaCursos() { return listaCursos; }
    public List<Object[]> getListaInstituicoes() { return listaInstituicoes; }
    public Curriculo getCurriculo() { return curriculo; }
    public void setCurriculo(Curriculo curriculo) { this.curriculo = curriculo; }
    public boolean isResumoExpandido() { return resumoExpandido; }
    public void setResumoExpandido(boolean resumoExpandido) { this.resumoExpandido = resumoExpandido; }
    public String getTermoPesquisaCurriculo() { return termoPesquisaCurriculo; }
    public void setTermoPesquisaCurriculo(String termoPesquisaCurriculo) { this.termoPesquisaCurriculo = termoPesquisaCurriculo; }
    public String getTermoPesquisaInstituicao() { return termoPesquisaInstituicao; }
    public void setTermoPesquisaInstituicao(String termoPesquisaInstituicao) { this.termoPesquisaInstituicao = termoPesquisaInstituicao; }
    public String getTermoPesquisaCurso() { return termoPesquisaCurso; }
    public void setTermoPesquisaCurso(String termoPesquisaCurso) { this.termoPesquisaCurso = termoPesquisaCurso; }
    public Usuario getUsuarioLogado() {
        return usuarioLogado;
    }
    public int getTamanhoPagina() { return tamanhoPagina; }
    public void setTamanhoPagina(int tamanhoPagina) {this.tamanhoPagina = tamanhoPagina; }
    public int getPaginaAtual() {return paginaAtual; }
    public void setPaginaAtual(int paginaAtual) { this.paginaAtual = paginaAtual; }
    public long getTotalCurriculos() { return totalCurriculos; }
    public void setTotalCurriculos(long totalCurriculos) { this.totalCurriculos = totalCurriculos; }
    public boolean isModoExportacao() { return modoExportacao; }
    public Set<Curriculo> getCurriculosSelecionados() { return curriculosSelecionados; }
}