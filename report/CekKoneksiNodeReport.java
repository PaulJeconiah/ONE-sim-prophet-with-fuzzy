/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package report;

import core.DTNHost;
import core.SimScenario;
import core.UpdateListener;
import java.util.List;
import routing.ProphetRouterFuzzy;

/**
 *
 * @author GeorgeBev
 */
public class CekKoneksiNodeReport extends Report implements UpdateListener{
    public CekKoneksiNodeReport() {
        super();
        super.init();
    }
    
    @Override
    public void updated(List<DTNHost> hosts) {
        
    }
    
    @Override 
    public void done() {
        List<DTNHost> listHosts = SimScenario.getInstance().getHosts();
        
        String tes = "";
        
        for(DTNHost h : listHosts) {
            ProphetRouterFuzzy router = (ProphetRouterFuzzy) h.getRouter();
            
            tes += h + " : " + router.connectedHost.size() + "\n";
        }
        
        write(tes);
        super.done();
    }
}
