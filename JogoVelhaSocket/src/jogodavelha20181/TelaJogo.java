package jogodavelha20181;

import java.awt.Color;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Random;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class TelaJogo extends javax.swing.JFrame {
    
    //variaveis jogadores e conexao
    private char[][] telaJogo = new char[3][3];  //tela do jogo com quantidade de "quadrados" 
    private boolean estaRodando;    //verifica se jogo está rodando
    private boolean jogadorConectado;  //verifica se o jogador está conectado
    private boolean vezAtual;       //mostra se é a vez do jogador
    private boolean ultimoJogoComecado;  //armazena se o jogador foi o iniciante da ultima jogada
    private boolean convidado;   //mostra se o jogador foi convidado
    private ServerSocket servidorTCP;      // socket servidor TCP 
    private ConexaoTCP conexaoTCP;      // conexão TCP com o outro jogador
    private String apelidoJogador;    // apelido do jogador local
    private DefaultListModel<JogadorOnLine> jogadoresOnline;  // lista de jogadores que estão online
    private final static Random random = new Random();   // gerador de números aleatórios
   
    //porta conexao UDP
    private final int UDP_PORTA = 20181; 
    
    //resultados jogo neutro
    private final int SEM_RESULTADO = -1;
    private final int SEM_GANHADOR = 0;
    private final int EMPATADO = 0;
    
    //resultados do jogo vitoria
    private final int GANHOU_LOCAL = 1;
    private final int GANHOU_REMOTO = 2;
    private final int GANHOU_NA_LINHA_1 = 1;
    private final int GANHOU_NA_LINHA_2 = 2;
    private final int GANHOU_NA_LINHA_3 = 3;
    private final int GANHOU_NA_COLUNA_1 = 4;
    private final int GANHOU_NA_COLUNA_2 = 5;
    private final int GANHOU_NA_COLUNA_3 = 6;
    private final int GANHOU_NA_DIAGONAL_PRINCIPAL = 7;
    private final int GANHOU_NA_DIAGONAL_SECUNDARIA = 8;

    //dados gerais
    public final char VAZIO = ' ';
    private final Color COR_EMPATE = new Color(255,64,129);
    
    //dados jogador local
    public final static int JOGADOR_LOCAL = 1;
    public final char POSICAO_JOGADOR_LOCAL = 'X';
    private final Color COR_LOCAL = new Color(106,27,154);
    
    //dados jogador remoto
    public final static int JOGADOR_REMOTO = 2;
    public final char POSICAO_JOGADOR_REMOTO = 'O';
    private final Color COR_REMOTO = new Color(255,109,0);    
    
    //jogo encerrado quando
    public final static int TEMPO_EXCEDIDO = 0;
    public final static int CONEXAO_PERDIDA = 1;
    public final static int JOGADOR_DESISTIU = 2;
    public final static int FIM = 3;
       
    private int[] resultados = new int[5];  // resultados de cada jogo
    private int jogoAtual;          // número do jogo atual

    // dados relacionados a threads e sockets
    private EscutaUDP threadEscutaUDP;         // thread para leitura da porta UDP
    private EscutaTCP threadEscutaTCP;         // thread de escuta da porta TCP
    private InetAddress enderecoLocal;             // endereço do jogador local
    private InetAddress enderecoBroad;         // endereço para broadcasting
    private InetAddress enderecoRemoto;     // endereço do jogador remoto
    private String apelidoRemoto;              // apelido do jogador remoto
    private Timer quemEstaOnLine;         // temporizador para saber quem está online
    private Timer timeoutQuemEstaOnlineTimer;  // temporizador de timeout
    private Timer timeoutAguardandoJogadorRemoto;    // temporizador de timeout
    
    // status do programa
    private boolean esperandoConexao;
    private boolean esperandoInicioJogo;
    private boolean esperandoConfirmacao;
    private boolean esperandoJogadorRemoto;
    private boolean esperandoRespostaConvite;
            
    // tipos de mensagens mostradas na tela
    public static final String MSG_IN = "IN";
    public static final String MSG_OUT = "OUT";
    public static final String MSG_ERRO = "ERRO";
    public static final String MSG_INFO = "INFO";
    public static final String MSG_PROTO_TCP = "TCP";
    public static final String MSG_PROTO_UDP = "UDP";
    public static final String MSG_PROTO_NENHUM = "";
    
    
    //tela propriamente dita
    public TelaJogo() {
        initComponents();
        
        // título da janela e centraliza
        this.setTitle("Jogo da Velha");        
        this.setLocationRelativeTo(null);
        
        // inicializa variáveis
        threadEscutaUDP = null;
        threadEscutaTCP = null;
        enderecoLocal = null;
        esperandoConexao = esperandoInicioJogo = false;
        esperandoConfirmacao = esperandoJogadorRemoto = false;
        esperandoRespostaConvite = false;
        estaRodando = jogadorConectado = false;
        servidorTCP = null;
        conexaoTCP = null;

        // cria endereço para broadcasting
        try {
            // envia mensagem avisando online
            enderecoBroad = InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException ex) {
            JOptionPane.showMessageDialog(null, "Broadcasting não foi possível.", "Finalizando programa",
                    JOptionPane.ERROR_MESSAGE);
            finalizaPrograma();
            return;
        }
        
        // cria lista de jogadores que estão online 
        jogadoresOnline = new DefaultListModel<>();
        jogadoresJList.setModel(jogadoresOnline);
        jogadoresJList.setCellRenderer(new JogadorJListRenderer());

        // Coleta e mostrar interfaces de rede cadastradas
        try
        {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets))
            {
                // descarta interfaces virtuais e loopback (127.0.0.1)
                if (netint.isVirtual() || netint.isLoopback()){
                    continue;
                }

                // endereços associados à interface
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                if(inetAddresses.hasMoreElements())
                {
                    for (InetAddress inetAddress : Collections.list(inetAddresses))
                    {
                        if ((inetAddress instanceof Inet4Address) &&
                            inetAddress.isSiteLocalAddress())
                        {
                            interfacesJComboBox.addItem(inetAddress.getHostAddress() +
                                    " - " + netint.getDisplayName());
                        }
                    }
                }
            }
        }catch(SocketException ex)
        {
        }
        
        // temporizador para atualização da lista de jogadores online
        ActionListener quemEstaOnlinePerformer = (ActionEvent evt) -> {
            for(int i = 0; i < jogadoresOnline.getSize(); ++i)
                jogadoresOnline.get(i).setAindaOnline(false);
            
            // envia mensagem para saber quem está online
            enviarMensagemUDP(enderecoBroad, 1, apelidoJogador);
            
            // dispara temporizador de timeout
            timeoutQuemEstaOnlineTimer.start();
        };
        quemEstaOnLine = new Timer(180000, quemEstaOnlinePerformer);
        quemEstaOnLine.setRepeats(true);   // temporizador repete indefinidamente
        
        // temporizador para timeout da atualização da lista de jogadores online
        // Quem não responder a MSG01 em 15 seg será considerado offline
        ActionListener timeoutQuemEstaOnlinePerformer = (ActionEvent evt) -> {
            atualizarListaJogadoresOnline();
        };
        timeoutQuemEstaOnlineTimer = new Timer(15000, timeoutQuemEstaOnlinePerformer);
        timeoutQuemEstaOnlineTimer.setRepeats(false);   // temporizador enviará notificação somente uma vez

        
        // temporizador para timeout de aguardando jogador remoto fazer
        // a conexão para iniciar o jogo ou jogador remoto responder
        // convite para jogar. Timeout de 30 segundos
        ActionListener timeoutAguardandoJogadorRemotoPerformer = (ActionEvent evt) -> {
            if(esperandoRespostaConvite)
                encerrarConviteParaJogar(true);
            else
                encerrarConexaoTCP(TEMPO_EXCEDIDO);
        };
        
        timeoutAguardandoJogadorRemoto = new Timer(30000, timeoutAguardandoJogadorRemotoPerformer);
        timeoutAguardandoJogadorRemoto.setRepeats(false);   // temporizador enviará notificação somente uma vez
    }
        
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel2 = new javax.swing.JLabel();
        jPanel13 = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        posicoesJPanel = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        pos1JLabel = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        pos2JLabel = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        pos3JLabel = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        pos4JLabel = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        pos5JLabel = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        pos6JLabel = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        pos7JLabel = new javax.swing.JLabel();
        jPanel11 = new javax.swing.JPanel();
        pos8JLabel = new javax.swing.JLabel();
        jPanel12 = new javax.swing.JPanel();
        pos9JLabel = new javax.swing.JLabel();
        jogadorLocalJLabel = new javax.swing.JLabel();
        jogadorRemotoJLabel = new javax.swing.JLabel();
        statusJLabel = new javax.swing.JLabel();
        placarLocalJLabel = new javax.swing.JLabel();
        placarRemotoJLabel = new javax.swing.JLabel();
        jogo1JLabel = new javax.swing.JLabel();
        jogo2JLabel = new javax.swing.JLabel();
        jogo3JLabel = new javax.swing.JLabel();
        jogo4JLabel = new javax.swing.JLabel();
        jogo5JLabel = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jogadoresJList = new javax.swing.JList<>();
        convidarJButton = new javax.swing.JButton();
        sairJButton = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        apelidoLocalJText = new javax.swing.JTextField();
        interfacesJComboBox = new javax.swing.JComboBox<>();
        conectarJButton = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        mensagensJTable = new javax.swing.JTable();

        jLabel2.setText("jLabel2");

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setLocationByPlatform(true);
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel1.setBackground(new java.awt.Color(255, 153, 255));
        jPanel1.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        jPanel4.setBackground(new java.awt.Color(204, 102, 255));
        jPanel4.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel4.setPreferredSize(new java.awt.Dimension(150, 150));

        pos1JLabel.setBackground(new java.awt.Color(255, 255, 255));
        pos1JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos1JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos1JLabel.setAlignmentY(0.0F);
        pos1JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos1JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos1JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addComponent(pos1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pos1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        jPanel5.setBackground(new java.awt.Color(204, 102, 255));
        jPanel5.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel5.setPreferredSize(new java.awt.Dimension(150, 150));

        pos2JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos2JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos2JLabel.setAlignmentY(0.0F);
        pos2JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos2JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos2JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pos2JLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 146, Short.MAX_VALUE)
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pos2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        jPanel6.setBackground(new java.awt.Color(204, 102, 255));
        jPanel6.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel6.setPreferredSize(new java.awt.Dimension(150, 150));

        pos3JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos3JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos3JLabel.setAlignmentY(0.0F);
        pos3JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos3JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos3JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(pos3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addComponent(pos3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel7.setBackground(new java.awt.Color(204, 102, 255));
        jPanel7.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel7.setPreferredSize(new java.awt.Dimension(150, 150));

        pos4JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos4JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos4JLabel.setAlignmentY(0.0F);
        pos4JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos4JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos4JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(pos4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addComponent(pos4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel8.setBackground(new java.awt.Color(204, 102, 255));
        jPanel8.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel8.setPreferredSize(new java.awt.Dimension(150, 150));

        pos5JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos5JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos5JLabel.setAlignmentY(0.0F);
        pos5JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos5JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos5JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(pos5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addComponent(pos5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel9.setBackground(new java.awt.Color(204, 102, 255));
        jPanel9.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel9.setPreferredSize(new java.awt.Dimension(150, 150));

        pos6JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos6JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos6JLabel.setAlignmentY(0.0F);
        pos6JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos6JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos6JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(pos6JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addComponent(pos6JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel10.setBackground(new java.awt.Color(204, 102, 255));
        jPanel10.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel10.setPreferredSize(new java.awt.Dimension(150, 150));

        pos7JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos7JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos7JLabel.setAlignmentY(0.0F);
        pos7JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos7JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos7JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(pos7JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addComponent(pos7JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel11.setBackground(new java.awt.Color(204, 102, 255));
        jPanel11.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel11.setPreferredSize(new java.awt.Dimension(150, 150));

        pos8JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos8JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos8JLabel.setAlignmentY(0.0F);
        pos8JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos8JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos8JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(pos8JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addComponent(pos8JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jPanel12.setBackground(new java.awt.Color(204, 102, 255));
        jPanel12.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));
        jPanel12.setPreferredSize(new java.awt.Dimension(150, 150));

        pos9JLabel.setFont(new java.awt.Font("Calibri", 1, 54)); // NOI18N
        pos9JLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        pos9JLabel.setAlignmentY(0.0F);
        pos9JLabel.setPreferredSize(new java.awt.Dimension(150, 150));
        pos9JLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                pos9JLabelMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(pos9JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addComponent(pos9JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout posicoesJPanelLayout = new javax.swing.GroupLayout(posicoesJPanel);
        posicoesJPanel.setLayout(posicoesJPanelLayout);
        posicoesJPanelLayout.setHorizontalGroup(
            posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, posicoesJPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addGroup(posicoesJPanelLayout.createSequentialGroup()
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(posicoesJPanelLayout.createSequentialGroup()
                        .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(posicoesJPanelLayout.createSequentialGroup()
                        .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel11, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel12, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        posicoesJPanelLayout.setVerticalGroup(
            posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(posicoesJPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel5, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel6, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel9, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel8, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(posicoesJPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jPanel12, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel10, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel11, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jogadorLocalJLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jogadorLocalJLabel.setForeground(new java.awt.Color(51, 153, 0));
        jogadorLocalJLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        jogadorLocalJLabel.setText("Local");
        jogadorLocalJLabel.setEnabled(false);

        jogadorRemotoJLabel.setFont(new java.awt.Font("Tahoma", 1, 18)); // NOI18N
        jogadorRemotoJLabel.setForeground(new java.awt.Color(255, 0, 0));
        jogadorRemotoJLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        jogadorRemotoJLabel.setText("Remoto");
        jogadorRemotoJLabel.setEnabled(false);

        statusJLabel.setFont(new java.awt.Font("Tahoma", 1, 11)); // NOI18N
        statusJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        placarLocalJLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        placarLocalJLabel.setForeground(new java.awt.Color(0, 0, 255));
        placarLocalJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        placarLocalJLabel.setText("0");
        placarLocalJLabel.setEnabled(false);

        placarRemotoJLabel.setFont(new java.awt.Font("Tahoma", 1, 24)); // NOI18N
        placarRemotoJLabel.setForeground(new java.awt.Color(0, 0, 255));
        placarRemotoJLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        placarRemotoJLabel.setText("0");
        placarRemotoJLabel.setEnabled(false);

        jogo1JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo1JLabel.setText("1");
        jogo1JLabel.setEnabled(false);

        jogo2JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo2JLabel.setText("2");
        jogo2JLabel.setEnabled(false);

        jogo3JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo3JLabel.setText("3");
        jogo3JLabel.setEnabled(false);

        jogo4JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo4JLabel.setText("4");
        jogo4JLabel.setEnabled(false);

        jogo5JLabel.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        jogo5JLabel.setText("5");
        jogo5JLabel.setEnabled(false);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(placarLocalJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jogo1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(jogo5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 8, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(placarRemotoJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addComponent(jogadorLocalJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(statusJLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jogadorRemotoJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 169, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
            .addComponent(posicoesJPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jogadorRemotoJLabel)
                        .addComponent(jogadorLocalJLabel))
                    .addComponent(statusJLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(placarLocalJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(placarRemotoJLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(jogo1JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo2JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo3JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo4JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(jogo5JLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(posicoesJPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
        );

        jPanel2.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true));

        jogadoresJList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jogadoresJList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                jogadoresJListValueChanged(evt);
            }
        });
        jScrollPane1.setViewportView(jogadoresJList);

        convidarJButton.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        convidarJButton.setText("Chamar para Partida");
        convidarJButton.setEnabled(false);
        convidarJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                convidarJButtonActionPerformed(evt);
            }
        });

        sairJButton.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        sairJButton.setText("Sair");
        sairJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sairJButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(sairJButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(convidarJButton, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 314, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 448, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(convidarJButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(sairJButton, javax.swing.GroupLayout.PREFERRED_SIZE, 35, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel2Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {convidarJButton, sairJButton});

        jPanel14.setBackground(new java.awt.Color(255, 204, 204));
        jPanel14.setBorder(javax.swing.BorderFactory.createTitledBorder("Jogador Local"));

        jLabel3.setText("Apelido:");

        jLabel4.setText("Dispositivo de Rede:");

        conectarJButton.setText("Conectar");
        conectarJButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                conectarJButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(apelidoLocalJText, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel4)
                .addGap(6, 6, 6)
                .addComponent(interfacesJComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 330, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(33, 33, 33)
                .addComponent(conectarJButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(conectarJButton)
                    .addComponent(jLabel4)
                    .addComponent(interfacesJComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(apelidoLocalJText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        mensagensJTable.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        mensagensJTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Tipo", "Protocolo", "Endereço", "Porta", "Conteúdo"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                true, false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        mensagensJTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_LAST_COLUMN);
        mensagensJTable.setFillsViewportHeight(true);
        mensagensJTable.setMinimumSize(new java.awt.Dimension(180, 64));
        mensagensJTable.setPreferredSize(null);
        mensagensJTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        mensagensJTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane2.setViewportView(mensagensJTable);
        mensagensJTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2)
                            .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 145, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void sairJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sairJButtonActionPerformed
        // encerra programa
        finalizaPrograma();
    }//GEN-LAST:event_sairJButtonActionPerformed

    private void finalizaPrograma()
    {
        // informa à rede que jogador local ficou offline
        enviarMensagemUDP(enderecoBroad, 3, apelidoJogador, true);
        
        Container frame = sairJButton.getParent();
        do
        {
            frame = frame.getParent(); 
        }while (!(frame instanceof JFrame));  
        ((JFrame)frame).dispose();
    }
    
    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // programa está sendo encerrado. Fazer os ajustes finais
        if(quemEstaOnLine.isRunning())
            quemEstaOnLine.stop();
        if(timeoutQuemEstaOnlineTimer.isRunning())
            timeoutQuemEstaOnlineTimer.stop();
        if(timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();

        // informa à rede que jogador local ficou offline
        enviarMensagemUDP(enderecoBroad, 3, apelidoJogador);
        
        // encerra thread de escuta da porta UDP
        if (threadEscutaUDP != null)
        {
            threadEscutaUDP.encerraConexao();
            threadEscutaUDP.cancel(true);
        }
        
        // encerra thread de escuta da porta TCP
        if (threadEscutaTCP != null)
        {
            threadEscutaTCP.encerraConexao();
            threadEscutaTCP.cancel(true);
        }
    }//GEN-LAST:event_formWindowClosing
            
    private void desconectaJogadorLocal()
    {
        jogadorConectado = false;
        
        // encerra temporizador de atualização da lista de jogadores online
        if(quemEstaOnLine.isRunning())
            quemEstaOnLine.stop();
        if(timeoutQuemEstaOnlineTimer.isRunning())
            timeoutQuemEstaOnlineTimer.stop();
        if(timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();
        
        // limpa lista de jogadores online
        jogadoresOnline.clear();
        
        // envia mensagem informando que jogador local ficou offline
        enviarMensagemUDP(enderecoBroad, 3, apelidoJogador);
        
        // habilita/desabilita controles
        apelidoLocalJText.setEnabled(true);
        interfacesJComboBox.setEnabled(true);
        conectarJButton.setText("Conectar");
        jogadorLocalJLabel.setEnabled(false);
        
//        // apaga apelido do jogador local no tabuleiro
//        jogadorLocalJLabel.setText(POSICAO_JOGADOR_LOCAL + " - Local");
        
        // encerra thread de leitura da porta UDP
        if (threadEscutaUDP != null)
        {
            threadEscutaUDP.encerraConexao();
            threadEscutaUDP.cancel(true);
        }
        
        // encerra thread de leitura da porta TCP
        if (threadEscutaTCP != null)
        {
            threadEscutaTCP.encerraConexao();
            threadEscutaTCP.cancel(true);
        }
       
        statusJLabel.setText("");
        apelidoLocalJText.requestFocus();
    }

    private void jogadoresJListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_jogadoresJListValueChanged
        int idx = jogadoresJList.getSelectedIndex();
        convidarJButton.setEnabled(idx >= 0);
    }//GEN-LAST:event_jogadoresJListValueChanged

    private void convidarJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_convidarJButtonActionPerformed
        JogadorOnLine j = jogadoresJList.getSelectedValue();
        if (j == null)
            return;
        
        // salva dados do jogador remoto
        apelidoRemoto = j.getApelido();
        enderecoRemoto = j.getAddress();
        
        // confirma convite
        statusJLabel.setText("");
        String msg = "Convidar " + apelidoRemoto + " para jogar?";
        int resp = JOptionPane.showConfirmDialog(this, msg, "Convite para jogar",
            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        // jogador local desistiu do convite
        if (resp == JOptionPane.NO_OPTION)
            return;

        // enviar convite para jogador remoto e iniciar temporizador para timeout
        enviarMensagemUDP(j.getAddress(), 4, apelidoJogador);
        esperandoRespostaConvite = true;
        statusJLabel.setText("AGUARDANDO RESPOSTA");
        timeoutAguardandoJogadorRemoto.start();
    }//GEN-LAST:event_convidarJButtonActionPerformed

    private void conectarJButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_conectarJButtonActionPerformed
        if(jogadorConectado)
        {
            desconectaJogadorLocal();
            return;
        }

        // apelido do jogador local
        apelidoJogador = apelidoLocalJText.getText().trim();
        if (apelidoJogador.isEmpty())
        {
            apelidoLocalJText.requestFocus();
            return;
        }

        // verifica se usuário escolheu a interface
        int nInterface = interfacesJComboBox.getSelectedIndex();
        if(nInterface < 0)
        {
            interfacesJComboBox.requestFocus();
            return;
        }

        // obtem endereço da interface de rede selecionada
        enderecoLocal = obtemInterfaceRede();
        if(enderecoLocal == null)
        {
            JOptionPane.showMessageDialog(null,
                "Erro na obtenção da interface escolhida.",
                "Conexão do jogador local",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // cria thread para leitura da porta UDP
        try
        {
            threadEscutaUDP = new EscutaUDP(this, UDP_PORTA, apelidoJogador,
                enderecoLocal);
        }catch(SocketException ex)
        {
            JOptionPane.showMessageDialog(null,
                "Erro na criação do thread de leitura da porta "+ UDP_PORTA +
                ".\n" + ex.getMessage(),
                "Conexão do jogador local",
                JOptionPane.ERROR_MESSAGE);
            finalizaPrograma();
            return;
        }

        jogadorConectado = true;

        // habilita/desabilita controles
        apelidoLocalJText.setEnabled(false);
        interfacesJComboBox.setEnabled(false);
        conectarJButton.setText("Desconectar");
        jogadorLocalJLabel.setEnabled(true);

//        // mostra apelido do jogador local no tabuleiro
//        jogadorLocalJLabel.setText(POSICAO_JOGADOR_LOCAL + " - " + apelidoLocal);

        // executa thread de leitura da porta UDP
        threadEscutaUDP.execute();

        // envia mensagem para todos os jogadores informando que
        // jogador local ficou online
        enviarMensagemUDP(enderecoBroad, 1, apelidoJogador);

        // inicia temporizador de atualização da lista de jogadores online
        if(quemEstaOnLine.isRunning() == false)
        quemEstaOnLine.start();

    }//GEN-LAST:event_conectarJButtonActionPerformed

    private void pos9JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos9JLabelMouseClicked
        escolhePosicao(9);
    }//GEN-LAST:event_pos9JLabelMouseClicked

    private void pos8JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos8JLabelMouseClicked
        escolhePosicao(8);
    }//GEN-LAST:event_pos8JLabelMouseClicked

    private void pos7JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos7JLabelMouseClicked
        escolhePosicao(7);
    }//GEN-LAST:event_pos7JLabelMouseClicked

    private void pos6JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos6JLabelMouseClicked
        escolhePosicao(6);
    }//GEN-LAST:event_pos6JLabelMouseClicked

    private void pos5JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos5JLabelMouseClicked
        escolhePosicao(5);
    }//GEN-LAST:event_pos5JLabelMouseClicked

    private void pos4JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos4JLabelMouseClicked
        escolhePosicao(4);
    }//GEN-LAST:event_pos4JLabelMouseClicked

    private void pos3JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos3JLabelMouseClicked
        escolhePosicao(3);
    }//GEN-LAST:event_pos3JLabelMouseClicked

    private void pos2JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos2JLabelMouseClicked
        escolhePosicao(2);
    }//GEN-LAST:event_pos2JLabelMouseClicked

    private void pos1JLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_pos1JLabelMouseClicked
        escolhePosicao(1);
    }//GEN-LAST:event_pos1JLabelMouseClicked
                
    private void encerrarConviteParaJogar(boolean timeout)
    {
        esperandoRespostaConvite = false;
        statusJLabel.setText("");
        
        String msg;
        if(timeout)
            msg = "Timeout: " + apelidoRemoto + " não respondeu convite.";
        else
            msg = apelidoRemoto + " recusou o convite.";
        JOptionPane.showMessageDialog(this, msg, "Convite para jogar",
                                      JOptionPane.INFORMATION_MESSAGE);
    }

    // jogador remoto respondeu convite para jogar
    public void respostaConvite(String msg, InetAddress addr)
    {
        // formato da resposta: Apelido|porta
        String[] strPartes= msg.split("\\|");
        if(strPartes.length != 2)
            return;
        
        // estou esperando uma resposta?
        if(esperandoRespostaConvite == false)
            return;
        
        // verifica se quem respondeu foi realmente o jogador remoto
        if ((addr.equals(enderecoRemoto) == false) ||
            apelidoRemoto.compareToIgnoreCase(strPartes[0]) != 0)
                return;

        // cancela espera da resposta ao convite
        esperandoRespostaConvite = false;
        if(timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();

        int porta = Integer.parseInt(strPartes[1]);
        if(porta == 0)
        {
            // jogador recusou o convite
            encerrarConviteParaJogar(false);
            return;
        }
        
        // jogador remoto aceitou convite: enviar mensagem de confirmação
        enviarMensagemUDP(addr, 6, "Ok");
        
        // conectar com jogador remoto via TCP
        try
        {
            // conecta com jogador remoto
            Socket socket = new Socket(addr, porta);
            conexaoTCP = new ConexaoTCP(this, socket);
            
            // inicia thread de comunicação
            conexaoTCP.execute();

            // atualiza variáveis e controles
            statusJLabel.setText("AGUARDANDO INÍCIO");
            esperandoInicioJogo = true;
        } catch(IOException ex)
        {
            String erro = "Erro ao criar conexão TCP com jogador remoto\n" +
                         ex.getMessage();
            JOptionPane.showMessageDialog(this, erro,
                    "Conectar com jogador remoto", JOptionPane.INFORMATION_MESSAGE);
        }
    }
        
    // jogador local escolhe uma posição livre no tabuleiro
    private void escolhePosicao(int pos)
    {
        // verifica se existe jogo em andamento e é vez do jogador corrente
        if (estaRodando == true && vezAtual == true)
            marcaPosicao(JOGADOR_LOCAL, pos);
    }

    public void marcaPosicao(int quemEscolheu, int pos)
    {
        // valida posição (só para garantir)
        if((pos < 1) || (pos > 9))
            return;
        
        // verifica se posição está vazia
        int linha = (pos - 1) / 3;
        int coluna = (pos - 1) % 3;
        if (telaJogo[linha][coluna] != VAZIO)
            return;
        
        Color cor;
        char marca;
        if(quemEscolheu == JOGADOR_LOCAL)
        {
            cor = COR_LOCAL;
            marca = POSICAO_JOGADOR_LOCAL;
        }
        else
        {
            cor = COR_REMOTO;
            marca = POSICAO_JOGADOR_REMOTO;
        }
        
        // preenche tabuleiro e mostra posição selecionada
        telaJogo[linha][coluna] = marca;
        javax.swing.JLabel ctrl = null;
        switch(pos)
        {
            case 1: ctrl = pos1JLabel; break;
            case 2: ctrl = pos2JLabel; break;
            case 3: ctrl = pos3JLabel; break;
            case 4: ctrl = pos4JLabel; break;
            case 5: ctrl = pos5JLabel; break;
            case 6: ctrl = pos6JLabel; break;
            case 7: ctrl = pos7JLabel; break;
            case 8: ctrl = pos8JLabel; break;
            case 9: ctrl = pos9JLabel; break;
        }
        ctrl.setForeground(cor);
        ctrl.setText(Character.toString(marca));
        
        // se quem escolheu a posição foi o jogador local,
        // enviar escolha para o jogador remoto
        if(quemEscolheu == JOGADOR_LOCAL)
            conexaoTCP.enviarMensagemTCP(8, String.valueOf(pos));
        
        // verifica se houve ganhador
        int ganhador = verificaGanhador();
        if(ganhador != SEM_GANHADOR)
        {
            resultados[jogoAtual - 1] = ganhador % 10;
            mostraResultadoPartida(ganhador);
            novaPartida(ganhador);
            return;
        }
        
        // verifica se jogo empatou
        if (jogoEmpatou())
        {
            resultados[jogoAtual - 1] = EMPATADO;
            mostraResultadoPartida(EMPATADO);
            novaPartida(EMPATADO);
            return;
        }
        
        // agora é a vez do outro jogador
        if (quemEscolheu == JOGADOR_LOCAL)
        {
            vezAtual = false;
            statusJLabel.setText("AGUARDANDO JOGADOR");
        }
        else
        {
            vezAtual = true;
            statusJLabel.setText("SUA VEZ");
        }
    }

    // processamento da mensagem MSG07: quem iniciará o jogo
    public void quemIniciaJogo(int jogador)
    {
        // atualiza controles e variáveis
        esperandoInicioJogo = false;
        
        iniciarSerieJogos();
        
        // verifica quem irá iniciar o jogo
        if (jogador == 1)
        {
            // jogador remoto iniciará o jogo
            vezAtual = ultimoJogoComecado = true;
            statusJLabel.setText("ESPERANDO JOGADOR");
        }
        else
        {
            // jogador local iniciará o jogo
            vezAtual = ultimoJogoComecado = true;
            statusJLabel.setText("SUA VEZ");
        }
    }

    private void mostraResultadoPartida(int quemGanhou)
    {
        // destaca no tabuleiro as posições vencedoras
        destacaResultadoTabuleiro(quemGanhou / 10);
        mostraResultados();
        String msg = "";
        switch(quemGanhou % 10)
        {
            case EMPATADO: msg = "Partida empatou!"; break;
            case GANHOU_LOCAL: msg = "Você ganhou!"; break;
            case GANHOU_REMOTO: msg = "Você perdeu!"; break;
        }
        
        if (jogoAtual == 5)
        {
            int local = Integer.parseInt(placarLocalJLabel.getText());
            int remoto = Integer.parseInt(placarRemotoJLabel.getText());
            msg += "\n\nPlacar final:" +
                   "\n    " + apelidoJogador + ": " + local +
                   "\n    " + apelidoRemoto + ": " + remoto +
                   "\n\n";
            if (local == remoto)
                msg += "Essa série ficou EMPATADA!";
            else
                if (local > remoto)
                    msg += "Você ganhou a série. Parabéns!";
                else
                    msg += apelidoRemoto + " ganhou a série!";
            
            msg += "\n\nPara jogar uma nova série,\njogador deverá ser convidado novamente.";
        }
        
        JOptionPane.showMessageDialog(this, msg, "Partida " + jogoAtual + " de 5.",
                                      JOptionPane.INFORMATION_MESSAGE);
    }
    
    // em caso de vitória, destaca no tabuleiro as posições que propiciaram
    // a vitória. Caso contrário, nenhuma posição será destacada
    private void destacaResultadoTabuleiro(int posicoesVencedoras)
    {
        boolean[][] destaca = {{false, false, false},
                               {false, false, false},
                               {false, false, false}};
        
        switch(posicoesVencedoras)
        {
            case GANHOU_NA_LINHA_1:
                destaca[0][0] = destaca[0][1] = destaca[0][2] = true;
                break;
            case GANHOU_NA_LINHA_2:
                destaca[1][0] = destaca[1][1] = destaca[1][2] = true;
                break;
            case GANHOU_NA_LINHA_3:
                destaca[2][0] = destaca[2][1] = destaca[2][2] = true;
                break;
            case GANHOU_NA_COLUNA_1:
                destaca[0][0] = destaca[1][0] = destaca[2][0] = true;
                break;
            case GANHOU_NA_COLUNA_2:
                destaca[0][1] = destaca[1][1] = destaca[2][1] = true;
                break;
            case GANHOU_NA_COLUNA_3:
                destaca[0][2] = destaca[1][2] = destaca[2][2] = true;
                break;
            case GANHOU_NA_DIAGONAL_PRINCIPAL:
                destaca[0][0] = destaca[1][1] = destaca[2][2] = true;
                break;
            case GANHOU_NA_DIAGONAL_SECUNDARIA:
                destaca[0][2] = destaca[1][1] = destaca[2][0] = true;
                break;
        }
        
        int linha, coluna;
        javax.swing.JLabel ctrl = null;
        for(int pos = 0; pos < 9; ++pos)
        {
            linha = pos / 3;
            coluna = pos % 3;
            switch(pos)
            {
                case 0: ctrl = pos1JLabel; break;
                case 1: ctrl = pos2JLabel; break;
                case 2: ctrl = pos3JLabel; break;
                case 3: ctrl = pos4JLabel; break;
                case 4: ctrl = pos5JLabel; break;
                case 5: ctrl = pos6JLabel; break;
                case 6: ctrl = pos7JLabel; break;
                case 7: ctrl = pos8JLabel; break;
                case 8: ctrl = pos9JLabel; break;
            }
            
            if (destaca[linha][coluna] == false)
                ctrl.setForeground(Color.DARK_GRAY);
        }
    }
    
    private int verificaGanhador()
    {
        // verifica linhas
        for(int linha = 0; linha < 3; ++linha)
        {
            if((telaJogo[linha][0] != VAZIO) &&
               (telaJogo[linha][0] == telaJogo[linha][1]) &&
               (telaJogo[linha][1] == telaJogo[linha][2]))
            {
                int resultado = 0;
                switch(linha)
                {
                    case 0: resultado = GANHOU_NA_LINHA_1; break;
                    case 1: resultado = GANHOU_NA_LINHA_2; break;
                    case 2: resultado = GANHOU_NA_LINHA_3; break;
                }
                return 10 * resultado +
                       (telaJogo[linha][0] == POSICAO_JOGADOR_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);
            }
        }
        
        // verifica colunas
        for(int coluna = 0; coluna < 3; ++coluna)
        {
            if((telaJogo[0][coluna] != VAZIO) &&
               (telaJogo[0][coluna] == telaJogo[1][coluna]) &&
               (telaJogo[1][coluna] == telaJogo[2][coluna]))
            {
                int resultado = 0;
                switch(coluna)
                {
                    case 0: resultado = GANHOU_NA_COLUNA_1; break;
                    case 1: resultado = GANHOU_NA_COLUNA_2; break;
                    case 2: resultado = GANHOU_NA_COLUNA_3; break;
                }
                
                return 10 * resultado +
                       (telaJogo[0][coluna] == POSICAO_JOGADOR_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);
            }
        }
        
        // verifica diagonal principal
        if((telaJogo[0][0] != VAZIO) &&
           (telaJogo[0][0] == telaJogo[1][1]) &&
           (telaJogo[1][1] == telaJogo[2][2]))
                return 10 * GANHOU_NA_DIAGONAL_PRINCIPAL +
                       (telaJogo[0][0] == POSICAO_JOGADOR_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);
        
        // verifica diagonal secundária
        if((telaJogo[0][2] != VAZIO) &&
           (telaJogo[0][2] == telaJogo[1][1]) &&
           (telaJogo[1][1] == telaJogo[2][0]))
                return 10 * GANHOU_NA_DIAGONAL_SECUNDARIA +
                       (telaJogo[0][2] == POSICAO_JOGADOR_LOCAL ? JOGADOR_LOCAL : JOGADOR_REMOTO);

        // não teve ganhador
        return SEM_GANHADOR;
    }
    
    // partida atual acabou: inicia nova partida ou encerra jogo
    private void novaPartida(int ultimoGanhador)
    {
        if (jogoAtual == 5)
        {
            // encerra jogo
            encerrarConexaoTCP(FIM);
            
            return;
        }
        
        // limpa tabuleiro para início de nova partida
        limpaTabuleiro();
        
        // inicia nova partida
        ++jogoAtual;
        mostraResultados();
        
        // jogador local envia mensagem de início de nova partida se:
        //     (1) jogador local tiver perdido a última partida
        //     (2) última partida tiver empatada e jogador remoto começou
        //         a partida anterior
        if (ultimoGanhador != JOGADOR_LOCAL)
        {
            boolean enviaMensagem = true;
            if(ultimoGanhador == EMPATADO)
                enviaMensagem = !ultimoJogoComecado;
            
            if (enviaMensagem)
            {
                // iniciar novo jogo
                conexaoTCP.enviarMensagemTCP(9, null);
                
                vezAtual = ultimoJogoComecado = true;
                statusJLabel.setText("SUA VEZ");
            }
        }
        else
        {
            // esperar jogador remoto iniciar novo jogo
            vezAtual = ultimoJogoComecado = false;
            statusJLabel.setText("AGUARDANDO INÍCIO");
            esperandoInicioJogo = true;
        }
    }
    
    public void jogadorRemotoIniciaNovoJogo()
    {
        esperandoInicioJogo = false;
        statusJLabel.setText("AGUARDANDO JOGADOR");
    }
    
    // se não existir nenhuma posição livre no tabuleiro, o jogo empatou
    private boolean jogoEmpatou()
    {
        for(int i = 0; i < 3; ++i)
            for(int j = 0; j < 3; ++j)
                if(telaJogo[i][j] == VAZIO)
                    return false;
        
        return true;
    }
    
    // o último parâmetro é opcional e indica se programa está encerrando.
    // Nesse caso, não deverá ser mostrado a mensagem UDP enviada, pois
    // a tabela não existe mais.
    public void enviarMensagemUDP(InetAddress addr, int numero,
                                  String compl, Boolean... encerra)
    {
        // verifica se último parâmetro foi informado. Se programa
        // estiver encerrando, não mostrar mensagem.
        boolean mostrarMensagens = true;
        if ((encerra.length > 0) && (encerra[0] instanceof Boolean))
            mostrarMensagens = !encerra[0];

        String msg;
        if((compl == null) || compl.isEmpty())
            msg = String.format("%02d005", numero);
        else
            msg = String.format("%02d%03d%s", numero, 5 + compl.length(),
                                compl);
        
        DatagramPacket p = new DatagramPacket(msg.getBytes(),
                        msg.getBytes().length, addr, UDP_PORTA);
        
        DatagramSocket udpSocket = null;
        try {
            // cria um socket do tipo datagram e liga-o a qualquer porta
            // disponível. Lembrando que PORTA_UDP local está ocupada
            udpSocket = new DatagramSocket(0, enderecoLocal);
            udpSocket.setBroadcast(addr.equals(enderecoBroad));
            
            // envia dados para o endereço e porta especificados no pacote
            udpSocket.send(p);                    
            
            // mostra mensagem enviada
            if(mostrarMensagens)
                mostraMensagem(MSG_OUT, MSG_PROTO_UDP, addr.getHostAddress(),
                               udpSocket.getLocalPort(), msg);
        } catch (IOException ex) {
            if(mostrarMensagens)
                mostraMensagem(MSG_OUT, MSG_PROTO_UDP,
                               addr.getHostAddress(),
                               (udpSocket == null ? 0 : udpSocket.getPort()),
                               "Erro: Envio da mensagem [msg " + numero + "]");
        }
    }
    
    // adiciona mensagem na tabela de mensagens
    public void mostraMensagem(String inORout, String protocolo,
                               String endereco, int porta, String conteudo)
    {
        DefaultTableModel model = (DefaultTableModel)mensagensJTable.getModel();
        model.addRow(new String[]{inORout, protocolo, endereco,
                                  (porta > 0 ? String.valueOf(porta) : ""),
                                  conteudo});
        
        // seleciona a linha que foi inserida
        mensagensJTable.changeSelection(mensagensJTable.getRowCount() - 1, 0,false,false);
    }

    public void atualizarListaJogadoresOnline()
    {
        for(int i = 0; i < jogadoresOnline.size(); ++i)
        {
            if(jogadoresOnline.get(i).getAindaOnline() == false)
                jogadoresOnline.remove(i);
        }
    }
    
    // insere ou confirma jogador na lista de jogadore online,
    // em ordem alfabética
    public void adicionaJogador(int nMsg, String apelido, InetAddress addr)
    {
        JogadorOnLine j;
        JogadorOnLine novoJogador;
        
        // percorre toda a lista
        for(int i = 0; i < jogadoresOnline.size(); ++i)
        {
            // jogador corrente
            j = jogadoresOnline.get(i);
            
            // verifica se jogador já está na lista
            if(j.mesmoApelido(apelido))
            {
                j.setAindaOnline(true); // jogador ainda está online
        
                // informar para o jogador que enviou o pacote que eu estou online
                if(nMsg == 1)
                    enviarMensagemUDP(addr, 2, apelidoJogador);
                
                return;
            }
            
            // adiciona jogador antes do jogador corrente
            if (j.getApelido().compareToIgnoreCase(apelido) > 0)
            {
                novoJogador = new JogadorOnLine(apelido, addr);
                jogadoresOnline.add(i, novoJogador);
        
                // informar para o jogador que enviou o pacote que eu estou online
                if(nMsg == 1)
                    enviarMensagemUDP(addr, 2, apelidoJogador);
                
                return;
            }
        }
        
        // insere jogador no final da lista
        novoJogador = new JogadorOnLine(apelido, addr);
        jogadoresOnline.addElement(novoJogador);
        
        // informar para o jogador que enviou o pacote que eu estou online
        if(nMsg == 1)
            enviarMensagemUDP(addr, 2, apelidoJogador);
    }
    
    // remove jogador da lista de jogadore online
    public void removeJogador(String apelido)
    {
        if(estaRodando && (apelido.compareToIgnoreCase(apelidoRemoto) == 0))
            encerrarConexaoTCP(JOGADOR_DESISTIU);
        
        // percorre toda a lista
        for(int i = 0; i < jogadoresOnline.size(); ++i)
        {
            // verifica se jogador foi encontrado
            if(jogadoresOnline.get(i).mesmoApelido(apelido))
            {
                jogadoresOnline.remove(i);
                return;
            }
        }
    }
    
    private InetAddress obtemInterfaceRede()
    {
        // verifica se usuário escolheu a interface
        int nInterface = interfacesJComboBox.getSelectedIndex();
        if(nInterface < 0)
            return null;

        // obtem interface selecionada pelo usuário
        String str = interfacesJComboBox.getItemAt(nInterface);
        String[] strParts = str.split(" - ");
        InetAddress addr;
        try {
            addr = InetAddress.getByName(strParts[0]);
        } catch (UnknownHostException ex)
        {
            return null;
        }
        
        return addr;
    }
    
    public void jogadorMeConvidou(String apelido, InetAddress addr)
    {
        // verifica se jogador local já está jogando
        String msg;
        if(estaRodando)
        {
            mostraMensagem(MSG_INFO, MSG_PROTO_NENHUM, addr.getHostAddress(),
                    -1, "Convite recusado automaticamente");
            
            // envia resposta automática recusando o convite
            msg = apelido + "|0";
            enviarMensagemUDP(addr, 5, msg);
            
            return;
        }
        
        // atualiza variáveis e controle
        convidado = true;
        statusJLabel.setText("");
        enderecoRemoto = null;
        
        // pergunta se jogador local aceita o convite
        msg = "O jogador " + apelido + " está te convidando para um jogo\nAceita?";
        int resp = JOptionPane.showConfirmDialog(this, msg, "Convite para jogar",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        
        // se jogador local recusar o convite, enviar
        // resposta de negação do convite
        if (resp == JOptionPane.NO_OPTION)
        {
            msg = apelidoJogador + "|0";
            enviarMensagemUDP(addr, 5, msg);
            mostraMensagem(MSG_INFO, MSG_PROTO_NENHUM, "", 0, "Convite não foi aceito");
            return;
        }
        
        // jogador local aceitou o convite. Criar servidor
        // TCP para jogador remoto se conectar
        servidorTCP = criarSocketTCP();
        if (servidorTCP == null)
        {
            JOptionPane.showMessageDialog(null,
                    "Erro na criação da conexão TCP.",
                    "Conexão do jogador remoto",
                    JOptionPane.ERROR_MESSAGE);
            
            // envia resposta de recusa do convite
            msg = apelidoJogador + "|0";
            enviarMensagemUDP(addr, 5, msg);
            statusJLabel.setText("");
            return;
        }
        
        // atualiza variáveis, cria thread para espera jogador remoto
        // conectar na conexão TCP criada
        enderecoRemoto = addr;
        apelidoRemoto = apelido;
        threadEscutaTCP = new EscutaTCP(this, servidorTCP, addr);
        threadEscutaTCP.execute();
            
        // envia resposta aceitando o convite
        msg = apelidoJogador + "|" + servidorTCP.getLocalPort();
        enviarMensagemUDP(addr, 5, msg);
        
        esperandoConexao = true;
        esperandoConfirmacao = true;
        esperandoInicioJogo = true;
        statusJLabel.setText("AGUARDANDO CONEXÃO");
        timeoutAguardandoJogadorRemoto.start();
    }
    
    public void jogadorRemotoConectou(ConexaoTCP conexao)
    {
        esperandoConexao = false;
        this.conexaoTCP = conexao;
        servidorTCP = null;     // servidor TCP foi encerrado
        iniciarSerieJogos();
    }
    
    public void jogadorRemotoConfirmou(InetAddress addr)
    {
        // verifica se quem confirmou foi realmente o jogador remoto
        if (addr.equals(enderecoRemoto) == false)
            return;

        esperandoConfirmacao = false;
        
        iniciarSerieJogos();
    }
    
    // cria e abre um socket TCP em uma porta qualquer na interface indicada
    private ServerSocket criarSocketTCP()
    {
        InetAddress addr = obtemInterfaceRede();
        if(addr == null)
            return null;

        ServerSocket socket;
        try
        {
            // cria um socket para servidor TCP.
            // Parâmetros:
            //     porta: 0 (usar uma porta que será alocada automaticamente)
            //   backlog: 1 (no máximo uma única conexão)
            //  bindAddr: addr (InetAddress local que o servidor irá ligar)
            socket = new ServerSocket(0, 1, addr);
            socket.setReuseAddress(true);
        } catch (IOException e)
        {
            return null;
        }
        
        return socket;
    }
    
    /**
     * Encerra o socket e thread criados para gerenciar a
     * conexão TCP estabelecida entre os jogadores local
     * e remoto durante um jogo.
     * 
     * @param motivo
     * Indica o motivo do encerramento da conexão, a saber:
     *      <dl>
     *      <dt>0: timeout (jogador remoto não conectou)</dt>
     *      <dt>1: conexão caiu;</dt>
     *      <dt>2: jogador remoto desistiu do jogo;</dt>
     *      <dt>3: fim do jogo</dt>
     *      </dl>
     */
    // timeout de conexão com jogador remoto
    public void encerrarConexaoTCP(int motivo)
    {
        // se jogador estiver jogando, encerra a série
        if(estaRodando)
        {
            estaRodando = false;
            zeraResultados();
            limpaTabuleiro();
        }
        
        // obtem dados para mostrar na tabela de mensagens
        int portaRemota = 0;
        String enderecoRemoto = "";
        if ((conexaoTCP != null) && (conexaoTCP.getSocket() != null))
        {
            portaRemota = conexaoTCP.getSocket().getPort();
            if (conexaoTCP.getSocket().getRemoteSocketAddress() != null)
                enderecoRemoto = conexaoTCP.getSocket().getRemoteSocketAddress().toString();
        }
        
        // encerra servidor TCP (socket e thread)
        try
        {
            if(servidorTCP != null)
                servidorTCP.close();
            
            if(threadEscutaTCP != null)
                threadEscutaTCP.cancel(true);
        } catch(IOException ex)
        {
        }
        servidorTCP = null;
        threadEscutaTCP = null;
        
        // encerra conexão TCP
        if (conexaoTCP != null)
            conexaoTCP.cancel(true);
        
        conexaoTCP = null;
        
        if(motivo == TEMPO_EXCEDIDO)
            JOptionPane.showMessageDialog(null,
                    "TIMEOUT: aguardando conexão remota.",
                    "Encerrar jogo",
                    JOptionPane.WARNING_MESSAGE);
        
        if(motivo == CONEXAO_PERDIDA)
            JOptionPane.showMessageDialog(null,
                    "Conexão com jogador remoto caiu.",
                    "Encerrar jogo",
                    JOptionPane.WARNING_MESSAGE);
        
        if (motivo == JOGADOR_DESISTIU)
            JOptionPane.showMessageDialog(null,
                    "Jogador remoto desistiu do jogo.",
                    "Encerrar jogo",
                    JOptionPane.WARNING_MESSAGE);
        
        esperandoConexao = esperandoInicioJogo = false;
        esperandoConfirmacao = esperandoJogadorRemoto = false;
        
        posicoesJPanel.setBackground(Color.DARK_GRAY);
        
        statusJLabel.setText("");
        
        mostraMensagem(MSG_INFO, MSG_PROTO_TCP, enderecoRemoto, portaRemota, "Conexão foi encerrada.");
        mostraMensagem(MSG_INFO, MSG_PROTO_NENHUM, "", 0, "Fim do jogo");
    }
    
    private void iniciarSerieJogos()
    {
        if (esperandoConexao || esperandoConfirmacao)
            return;
        
        // encerra temporizador de timeout
        if (timeoutAguardandoJogadorRemoto.isRunning())
            timeoutAguardandoJogadorRemoto.stop();
        
        // atualiza tela
        jogadorRemotoJLabel.setText(apelidoRemoto + " - " + POSICAO_JOGADOR_REMOTO);
        jogadorRemotoJLabel.setEnabled(true);
        
        if (convidado)
        {
            // envia mensagem MSG07 para jogador remoto
            int n = random.nextInt(2) + 1;
            if (n == JOGADOR_LOCAL)
            {
                // jogador local irá iniciar o jogo
                vezAtual = ultimoJogoComecado = true;
                statusJLabel.setText("SUA VEZ");
            }
            else
            {
                // jogador remoto irá iniciar o jogo
                vezAtual = ultimoJogoComecado = false;
                statusJLabel.setText("ESPERANDO JOGADOR");
            }
            String compl = String.valueOf(n);
            conexaoTCP.enviarMensagemTCP(7, compl);
        }
        
        estaRodando = true;
        jogoAtual = 1;
        zeraResultados();

        limpaTabuleiro();
        
        placarLocalJLabel.setEnabled(true);
        placarRemotoJLabel.setEnabled(true);
    }
    
    private void limpaTabuleiro()
    {
        // limpa tabuleiro
        int pos = 0;
        for(int i = 0; i < 3; ++i)
        {
            for(int j = 0; j < 3; ++j)
            {
                telaJogo[i][j] = VAZIO;
                switch(pos)
                {
                    case 0: pos1JLabel.setText(""); break;
                    case 1: pos2JLabel.setText(""); break;
                    case 2: pos3JLabel.setText(""); break;
                    case 3: pos4JLabel.setText(""); break;
                    case 4: pos5JLabel.setText(""); break;
                    case 5: pos6JLabel.setText(""); break;
                    case 6: pos7JLabel.setText(""); break;
                    case 7: pos8JLabel.setText(""); break;
                    case 8: pos9JLabel.setText(""); break;
                }
                ++pos;
            }
        }
    }
    
    public void zeraResultados()
    {
        String nomeRemoto;
        Color corTabuleiro;
        if (estaRodando)
        {
            nomeRemoto = apelidoRemoto + " - " + POSICAO_JOGADOR_REMOTO;
            corTabuleiro = Color.BLACK;
        }
        else
        {
            nomeRemoto = "Remoto - " + POSICAO_JOGADOR_REMOTO;
            corTabuleiro = Color.DARK_GRAY;
        }
        
        jogadorRemotoJLabel.setText(nomeRemoto);
        posicoesJPanel.setBackground(corTabuleiro);
        
        // limpa resultados de todos os jogos
        for(int i = 0; i < 5; ++i)
            resultados[i] = SEM_RESULTADO;
        mostraResultados();
        
        // limpa tabuleiro
        int pos = 0;
        for(int i = 0; i < 3; ++i)
        {
            for(int j = 0; j < 3; ++j)
            {
                telaJogo[i][j] = VAZIO;
                switch(pos)
                {
                    case 0: pos1JLabel.setText(""); break;
                    case 1: pos2JLabel.setText(""); break;
                    case 2: pos3JLabel.setText(""); break;
                    case 3: pos4JLabel.setText(""); break;
                    case 4: pos5JLabel.setText(""); break;
                    case 5: pos6JLabel.setText(""); break;
                    case 6: pos7JLabel.setText(""); break;
                    case 7: pos8JLabel.setText(""); break;
                    case 8: pos9JLabel.setText(""); break;
                }
                ++pos;
            }
        }
        
        jogadorRemotoJLabel.setEnabled(estaRodando);
        placarLocalJLabel.setEnabled(estaRodando);
        placarRemotoJLabel.setEnabled(estaRodando);
    }
    
    public void mostraResultados()
    {
        javax.swing.JLabel ctrlLabel = null;
        Color cor;
        int local = 0, remoto = 0;
        for(int i = 0; i < 5; ++i)
        {
            switch(i)
            {
                case 0: ctrlLabel = jogo1JLabel; break;
                case 1: ctrlLabel = jogo2JLabel; break;
                case 2: ctrlLabel = jogo3JLabel; break;
                case 3: ctrlLabel = jogo4JLabel; break;
                case 4: ctrlLabel = jogo5JLabel; break;
            }
            
            cor = Color.DARK_GRAY;
            if (estaRodando)
            {
                if(((i + 1) == jogoAtual) && (resultados[i] == SEM_RESULTADO))
                    cor = Color.BLACK;
                else
                {
                    switch (resultados[i])
                    {
                        case GANHOU_LOCAL:
                            ++local;
                            cor = COR_LOCAL;
                            break;
                        case GANHOU_REMOTO:
                            ++remoto;
                            cor = COR_REMOTO;
                            break;
                        default:
                            // empate
                            cor = COR_EMPATE;
                            break;
                    }
                }
                
                ctrlLabel.setEnabled((i + 1) <= jogoAtual);
            }
            else
                ctrlLabel.setEnabled(false);
            ctrlLabel.setForeground(cor);
        }
        
        placarLocalJLabel.setText(String.valueOf(local));
        placarRemotoJLabel.setText(String.valueOf(remoto));
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new TelaJogo().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField apelidoLocalJText;
    private javax.swing.JButton conectarJButton;
    private javax.swing.JButton convidarJButton;
    private javax.swing.JComboBox<String> interfacesJComboBox;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel jogadorLocalJLabel;
    private javax.swing.JLabel jogadorRemotoJLabel;
    private javax.swing.JList<JogadorOnLine> jogadoresJList;
    private javax.swing.JLabel jogo1JLabel;
    private javax.swing.JLabel jogo2JLabel;
    private javax.swing.JLabel jogo3JLabel;
    private javax.swing.JLabel jogo4JLabel;
    private javax.swing.JLabel jogo5JLabel;
    private javax.swing.JTable mensagensJTable;
    private javax.swing.JLabel placarLocalJLabel;
    private javax.swing.JLabel placarRemotoJLabel;
    private javax.swing.JLabel pos1JLabel;
    private javax.swing.JLabel pos2JLabel;
    private javax.swing.JLabel pos3JLabel;
    private javax.swing.JLabel pos4JLabel;
    private javax.swing.JLabel pos5JLabel;
    private javax.swing.JLabel pos6JLabel;
    private javax.swing.JLabel pos7JLabel;
    private javax.swing.JLabel pos8JLabel;
    private javax.swing.JLabel pos9JLabel;
    private javax.swing.JPanel posicoesJPanel;
    private javax.swing.JButton sairJButton;
    private javax.swing.JLabel statusJLabel;
    // End of variables declaration//GEN-END:variables
}
