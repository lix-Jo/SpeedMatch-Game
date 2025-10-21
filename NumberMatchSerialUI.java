import com.fazecast.jSerialComm.SerialPort;
import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class NumberMatchSerialUI extends JFrame {
    private JComboBox<SerialPort> portBox;
    private JButton connectBtn, startBtn;
    private JLabel roundLbl, statusLbl, resultLbl, timeBigLbl, instructionLbl;
    private final List<JButton> digitBtns = new ArrayList<>();

    private SerialPort port;
    private OutputStream out;
    private InputStream in;

    private int rounds = 0, currentDigit = -1;
    private long startNano = 0L;
    private final List<Long> timesMs = new ArrayList<>();
    private final StringBuilder lineBuf = new StringBuilder();

    public NumberMatchSerialUI() {
        super("Number Match (Java + Arduino)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10,10));

        // ===== Top bar (ALWAYS VISIBLE) =====
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT,8,8));
        portBox = new JComboBox<>(SerialPort.getCommPorts());
        portBox.setRenderer((list,v,i,s,f)->new JLabel(v==null?"":v.getSystemPortName()));
        for (int i=0;i<portBox.getItemCount();i++){
            if (portBox.getItemAt(i).getSystemPortName().equalsIgnoreCase("COM7")){
                portBox.setSelectedIndex(i); break;
            }
        }
        connectBtn = new JButton("Connect");
        connectBtn.addActionListener(e->onConnectToggle());
        startBtn = new JButton("START");
        startBtn.setEnabled(false); // راح تتفعل بعد الاتصال
        startBtn.addActionListener(e->startGame());
        roundLbl = new JLabel("Round: 0/3");

        top.add(new JLabel("Port:"));
        top.add(portBox);
        top.add(connectBtn);
        top.add(startBtn);
        top.add(roundLbl);
        add(top, BorderLayout.NORTH);

        // ===== Center column (instruction + time + grid) =====
        JPanel centerCol = new JPanel();
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        instructionLbl = new JLabel("Choose the number that appears to you", SwingConstants.CENTER);
        instructionLbl.setFont(new Font("Arial", Font.BOLD, 18));
        instructionLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerCol.add(instructionLbl);

        timeBigLbl = new JLabel("0.000 s", SwingConstants.CENTER);
        timeBigLbl.setFont(new Font("Arial", Font.BOLD, 44));
        timeBigLbl.setForeground(new Color(200, 0, 0)); // RED
        timeBigLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        timeBigLbl.setBorder(BorderFactory.createEmptyBorder(6, 0, 12, 0));
        centerCol.add(timeBigLbl);

        JPanel grid = new JPanel(new GridLayout(2,5,8,8));
        Font f = new Font("Arial", Font.BOLD, 22);
        for (int i=0;i<10;i++){
            JButton b = new JButton(String.valueOf(i));
            b.setFont(f);
            b.setEnabled(false);
            final int n=i;
            b.addActionListener(e->onDigitClick(n));
            digitBtns.add(b);
            grid.add(b);
        }
        centerCol.add(grid);

        add(centerCol, BorderLayout.CENTER);

        // ===== Bottom status =====
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        statusLbl = new JLabel("Select COM7 → Connect → START");
        resultLbl = new JLabel(" ");
        resultLbl.setForeground(new Color(200,0,0)); // RED for final times
        bottom.add(statusLbl);
        bottom.add(resultLbl);
        add(bottom, BorderLayout.SOUTH);

        setSize(760, 460);
        setLocationRelativeTo(null);

        addWindowListener(new java.awt.event.WindowAdapter(){
            public void windowClosing(java.awt.event.WindowEvent e){
                sendSafe("CLEAR");
                if(port!=null&&port.isOpen()) port.closePort();
            }
        });

        // Non-blocking serial reader
        new javax.swing.Timer(20, e->pollSerial()).start();
    }

    private void onConnectToggle(){
        try{
            if(port!=null && port.isOpen()){
                sendSafe("CLEAR");
                port.closePort();
                port=null; out=null; in=null;
                connectBtn.setText("Connect");
                startBtn.setEnabled(false);
                setDigitsEnabled(false);
                statusLbl.setText("Disconnected.");
                return;
            }
            SerialPort p = (SerialPort) portBox.getSelectedItem();
            if(p==null){ statusLbl.setText("No port selected."); return; }
            p.setBaudRate(115200);
            p.setNumDataBits(8);
            p.setNumStopBits(SerialPort.ONE_STOP_BIT);
            p.setParity(SerialPort.NO_PARITY);
            p.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING,0,0);
            if(!p.openPort()){ statusLbl.setText("Failed to open port."); return; }
            port=p; out=p.getOutputStream(); in=p.getInputStream();
            connectBtn.setText("Disconnect");
            startBtn.setEnabled(true); // الآن تقدرين تضغطين START
            statusLbl.setText("Connected: " + p.getSystemPortName());
            sendSafe("CLEAR");
        }catch(Exception ex){
            statusLbl.setText("Connect error: " + ex.getMessage());
        }
    }

    private void startGame(){
        rounds=0; timesMs.clear();
        resultLbl.setText(" ");
        roundLbl.setText("Round: 0/3");
        statusLbl.setText("Starting...");
        timeBigLbl.setText("0.000 s"); // reset
        setDigitsEnabled(false);
        sendSafe("START");
    }

    private void onDigitClick(int chosen){
        setDigitsEnabled(false);
        long elapsedMs = Math.round((System.nanoTime()-startNano)/1_000_000.0);
        double sec = elapsedMs / 1000.0;
        timesMs.add(elapsedMs);

        // تحديث العرض الكبير + نتيجة الجولة (بالأحمر)
        timeBigLbl.setText(String.format("%.3f s", sec));
        if(chosen==currentDigit) {
            resultLbl.setText(String.format("Correct! %.3f s", sec));
        } else {
            resultLbl.setText(String.format("Wrong (%d ≠ %d)  %.3f s", chosen, currentDigit, sec));
        }

        sendSafe("NEXT");
    }

    private void handleLine(String line){
        line=line.trim();
        if(line.startsWith("DIGIT:")){
            try{ currentDigit=Integer.parseInt(line.substring(6)); }
            catch(Exception ignored){ currentDigit=-1; }
            rounds++;
            roundLbl.setText("Round: "+rounds+"/3");
            statusLbl.setText("Look at 7-seg and click the same number!");
            startNano = System.nanoTime();
            setDigitsEnabled(true);
            startBtn.setEnabled(false);
            timeBigLbl.setText("0.000 s");
        } else if(line.equals("DONE")){
            startBtn.setEnabled(true);
            setDigitsEnabled(false);
            if(timesMs.isEmpty()) {
                statusLbl.setText("Done. No attempts.");
                timeBigLbl.setText("0.000 s");
            } else {
                long sum=0; for(long t:timesMs) sum+=t;
                double avgSec = (sum / 1000.0) / timesMs.size();
                statusLbl.setText(String.format("Done! Average: %.3f s", avgSec));

                // عرض الأزمنة بالثواني كلها باللون الأحمر
                StringBuilder sb = new StringBuilder("Times: [");
                for (int i=0;i<timesMs.size();i++){
                    if(i>0) sb.append(", ");
                    sb.append(String.format("%.3f", timesMs.get(i)/1000.0));
                }
                sb.append("] s");
                resultLbl.setText(sb.toString());
            }
        }
    }

    private void pollSerial(){
        if(in==null) return;
        try{
            int avail = port.bytesAvailable();
            if(avail<=0) return;
            byte[] buf=new byte[avail];
            int n=in.read(buf); if(n<=0) return;
            for(int i=0;i<n;i++){
                char c=(char)buf[i];
                if(c=='\n'||c=='\r'){
                    if(lineBuf.length()>0){
                        String s=lineBuf.toString();
                        lineBuf.setLength(0);
                        handleLine(s);
                    }
                } else lineBuf.append(c);
            }
        }catch(Exception ignored){}
    }

    private void sendSafe(String s){
        try{ if(out!=null){ out.write((s+"\n").getBytes()); out.flush(); } }
        catch(Exception e){ statusLbl.setText("Serial write failed."); }
    }
    private void setDigitsEnabled(boolean on){ for(JButton b:digitBtns) b.setEnabled(on); }

    public static void main(String[] args){
        SwingUtilities.invokeLater(()-> new NumberMatchSerialUI().setVisible(true));
    }
}
