/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package report;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.SimScenario;
import core.UpdateListener;
import java.util.*;
import routing.ProphetRouterFuzzy;

/**
 *
 * @author GeorgeBev
 */
public class CekSisaBufferReport extends Report implements UpdateListener {
    public static final String BUFFER_REPORT_INTERVAL = "occupancyInterval";
    public static final int DEFAULT_BUFFER_REPORT_INTERVAL = 600;
    private int lastRecord = 0;
    private final int interval;
    public CekSisaBufferReport() {
        super();
        Settings s = new Settings();
        if (s.contains(BUFFER_REPORT_INTERVAL)) {
            interval = s.getInt(BUFFER_REPORT_INTERVAL);
        } else {
            interval = DEFAULT_BUFFER_REPORT_INTERVAL;
        }
    }
    
    @Override
    public void done() {
//        List<DTNHost> listHosts = SimScenario.getInstance().getHosts();
//        
//        String tes = "";
//        
//        for(DTNHost h : listHosts) {
//            ProphetRouterFuzzy rtr = (ProphetRouterFuzzy) h.getRouter();
//            
//            tes += h + " : " + rtr.getFreeBufferS(h) + "\n";
//        }
//        
//        write(tes);
//        super.done();
    }

    @Override
    public void updated(List<DTNHost> hosts) {
        if(SimClock.getTime() - lastRecord >= interval) {
            for(DTNHost h : hosts) {
                ProphetRouterFuzzy router = (ProphetRouterFuzzy) h.getRouter();
                double value = router.getFreeBufferS(h);
                if(h.toString().equals("P34")) {
                    printLine(lastRecord, value);
                }
            }
            lastRecord = (int) SimClock.getTime();
        }
    }
    
    private void printLine(int time, double value) {
        String tes = "";
        tes += time + "\t" + value + "\n";
        write(tes);
    }
}
