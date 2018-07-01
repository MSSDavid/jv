package jogodavelha20181;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import javax.swing.SwingWorker;

/**
 * Esta classe implementa a escuta das mensagens enviadas via UDP no jogo.
 */

public class EscutaUDP extends SwingWorker<Void, String>  {
    private TelaJogo mainFrame;   // frame principal do programa
    private String apelidoLocal;        // apelido do jogador local
    private DatagramSocket udpSocket;
    private int porta;
    private InetAddress addrLocal;

    public EscutaUDP(TelaJogo mainFrame, int porta,
                     String apelidoLocal, InetAddress addr) throws SocketException
    {
        this.mainFrame = mainFrame; // Ele opera na janela principal da aplicação. 
        this.porta = porta; // A porta para a conexão
       
        this.apelidoLocal = apelidoLocal;
        // Apelido do jogador local. É importante conhecer este apelido para que 
        // mensagens enviadas não sejam interpretadas como de um jogador remoto.

        this.addrLocal = addr; // Endereço local da execução do programa.
        udpSocket = new DatagramSocket(porta, addr);
        udpSocket.setReuseAddress(true);
        // Quem cuida das portas é o SO. Quando hã uma porta aberta (que pode 
        // ter sido utilizada numa execução ou programa anterior, o programa 
        // tenta reutilizá-la.
        

    }

    @Override
    protected Void doInBackground() throws Exception {
        /**
         * Esta classe opera em segundo plano. Ela opera a conexão UDP de forma
         * que os dados sejam enviados em tempo real para a conexão do jogo.
         * Essa parte do programa, deve funcionar continuamente enquando o jogo 
         * é executado.
         */
        
        String msg; // As mensagens são enviadas por uma String simples.

        while (true)
        {   
            // Pacote de dados que serão enviados.
            byte[] buf = new byte[256];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            // bloqueia até que um pacote seja lido
            try {
                udpSocket.receive(packet);
            } catch (IOException ex)
            {
                mainFrame.mostraMensagem(TelaJogo.MSG_IN,
                        TelaJogo.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(), ex.getMessage());
                continue;
            }
            
            // obtém dados
            msg = new String(packet.getData()).trim();
            
            // A mensagem recebida é mostrada. 
            mainFrame.mostraMensagem(TelaJogo.MSG_IN,
                        TelaJogo.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(), msg);
            
            // Uma mensagem precisa ter ao menos 5 caracteres para ser processada.
            if (msg.length() < 5)
            {
                mainFrame.mostraMensagem(TelaJogo.MSG_IN,
                        TelaJogo.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(), "Mensagem inválida [" + msg + "]");
                continue;
            }
            
            // Aqui há um primeiro processamento da mensagem enviada, em relaçção
            // tamanho dela. 
            int tam = Integer.parseInt(msg.substring(2, 5));
            if (msg.length() != tam)
            {
                mainFrame.mostraMensagem(TelaJogo.MSG_IN,
                        TelaJogo.MSG_PROTO_UDP,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        "Erro: tamanho da mensagem [" + msg + "]");
                continue;
            }

            // Aqui se justifica a importância de se conhecer o endereço do jogador 
            // local, para que suas mensagens não sejam interpretadas como de um 
            // outro jogador.
            if(packet.getAddress().equals(addrLocal))
                continue;
            
            String complemento = "";
            if (tam > 5)
                complemento = msg.substring(5);
            
            int nMsg = Integer.parseInt(msg.substring(0, 2));
            
            // As mensagens enviadas são devidamente processadas nesse bloco switch.
            switch(nMsg)
            {
                case 1:
                case 2:
                    // Essa mensagem faz o jogador entrar no jogo.
                    mainFrame.adicionaJogador(nMsg, complemento, packet.getAddress()); break;
                    
                case 3:
                    // Aqui, há a remoção do jogador.
                    mainFrame.removeJogador(complemento);
                    break;
                    
                case 4:
                    // Aqui, há o envio da notificação do convite.
                    mainFrame.jogadorMeConvidou(complemento, packet.getAddress());
                    break;
                    
                case 5:
                    // Aqui, o jogador responde ao convite.
                    mainFrame.respostaConvite(complemento, packet.getAddress());
                    break;
                    
                case 6:
                    // Aqui há a confirmação de que foi feito o aceite do convite para a partida.
                    mainFrame.jogadorRemotoConfirmou(packet.getAddress());
                    break;
                    
                default:
                    // No caso de haver uma mensagem inválida, ela será mostrada.
                    mainFrame.mostraMensagem(TelaJogo.MSG_IN,
                                TelaJogo.MSG_PROTO_UDP,
                                packet.getAddress().getHostAddress(),
                                packet.getPort(),
                                "Mensagem inválida [" + msg + "]");
            }
        }
    }
    
    public void encerraConexao()
    {
        // Aqui o socket é fechado. 
        if (udpSocket.isConnected())
            udpSocket.disconnect();
        
        udpSocket.close();
    }
}
