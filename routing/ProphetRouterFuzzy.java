/* 
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details. 
 */
package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.ModuleCommunicationBus;
import core.ModuleCommunicationListener;
import core.NetworkInterface;
import core.Settings;
import core.SimClock;
//import core.SimError;
import core.SimScenario;
import core.Tuple;
import java.util.HashSet;
import java.util.Set;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import net.sourceforge.jFuzzyLogic.rule.Variable;

/**
 * Implementation of PRoPHET router as described in
 * <I>Probabilistic routing in intermittently connected networks</I> by Anders
 * Lindgren et al.
 */
public class ProphetRouterFuzzy extends ActiveRouter implements ModuleCommunicationListener {

    /**
     * delivery predictability initialization constant
     */
    public static final double P_INIT = 0.75;
    /**
     * delivery predictability transitivity scaling constant default value
     */
    public static final double DEFAULT_BETA = 0.25;
    /**
     * delivery predictability aging constant
     */
    public static final double GAMMA = 0.98;

    /**
     * Prophet router's setting namespace ({@value})
     */
    public static final String PROPHET_NS = "ProphetRouter";
    /**
     * Number of seconds in time unit -setting id ({@value}). How many seconds
     * one time unit is when calculating aging of delivery predictions. Should
     * be tweaked for the scenario.
     */
    public static final String SECONDS_IN_UNIT_S = "secondsInTimeUnit";

    /**
     * Transitivity scaling constant (beta) -setting id ({@value}). Default
     * value for setting is {@link #DEFAULT_BETA}.
     */
    public static final String BETA_S = "beta";

    /**
     * the value of nrof seconds in time unit -setting
     */
    private int secondsInTimeUnit;
    /**
     * value of beta setting
     */
    private double beta;

    /**
     * delivery predictabilities
     */
    private Map<DTNHost, Double> preds;
    /**
     * last delivery predictability update (sim)time
     */
    private double lastAgeUpdate;

    /**
     * New Added
     */
    public FIS fcl;
    public static final String FCL = "fcl";
    public static final String CURRENT_ENERGY = "current_Energy";
    public static final String FREE_BUFFER = "free_Buffer";
    public static final String TRANSFER_OF_UTILITY = "ToU";

    private final double[] initEnergy;
    public double currentEnergy;
    private double warmupTime;
    private double lastScanUpdate;
    private double lastUpdate;
    private double scanEnergy;
    private double transmitEnergy;
    private double scanResponseEnergy;
    ModuleCommunicationBus comBus;
    private static Random rng = null;
    public static final String ENERGY_VALUE_ID = "Energy.value";
    public static final String SCAN_ENERGY_S = "scanEnergy";
    public static final String TRANSMIT_ENERGY_S = "transmitEnergy";
    public static final String SCAN_RESPONSE_ENERGY_S = "scanResponseEnergy";
    public static final String INIT_ENERGY_S = "intialEnergy";

    public Set<DTNHost> connectedHost;

    /**
     * Constructor. Creates a new message router based on the settings in the
     * given Settings object.
     *
     * @param s The settings object
     */
    public ProphetRouterFuzzy(Settings s) {
        super(s);
        String fclString = s.getSetting(FCL);
        fcl = FIS.load(fclString);
        this.scanEnergy = s.getDouble(SCAN_ENERGY_S);
        this.scanResponseEnergy = s.getDouble(SCAN_RESPONSE_ENERGY_S);
        this.transmitEnergy = s.getDouble(TRANSMIT_ENERGY_S);
        this.initEnergy = s.getCsvDoubles(INIT_ENERGY_S);
        Settings prophetSettings = new Settings(PROPHET_NS);
        secondsInTimeUnit = prophetSettings.getInt(SECONDS_IN_UNIT_S);
        beta = prophetSettings.contains(BETA_S) ? prophetSettings.getDouble(BETA_S) : DEFAULT_BETA;
        this.setEnergy(this.initEnergy);
        initPreds();

        this.connectedHost = new HashSet<>();
    }

    /**
     * Copyconstructor.
     *
     * @param r The router prototype where setting values are copied from
     */
    protected ProphetRouterFuzzy(ProphetRouterFuzzy r) {
        super(r);
        this.initEnergy = r.initEnergy;
        this.secondsInTimeUnit = r.secondsInTimeUnit;
        this.beta = r.beta;
        this.fcl = r.fcl;
        this.lastScanUpdate = 0;
        this.scanEnergy = r.scanEnergy;
        this.scanResponseEnergy = r.scanResponseEnergy;
        this.comBus = null;
        this.warmupTime = r.warmupTime;
        this.transmitEnergy = r.transmitEnergy;
//        this.preds = new HashMap<>(r.preds);
        this.currentEnergy = r.currentEnergy;
        this.setEnergy(r.initEnergy);
        this.connectedHost = new HashSet<>();
        initPreds();
    }

    /**
     * Initializes predictability hash
     */
    private void initPreds() {
        this.preds = new HashMap();
    }

    @Override
    public void changedConnection(Connection con) {
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            updateDeliveryPredFor(otherHost);
            updateTransitivePreds(otherHost);

//            this.connectedHost.add(otherHost);
        }
    }

    /**
     * Updates delivery predictions for a host.
     * <CODE>P(a,b) = P(a,b)_old + (1 - P(a,b)_old) * P_INIT</CODE>
     *
     * @param host The host we just met
     */
    private void updateDeliveryPredFor(DTNHost host) {
        double oldValue = getPredFor(host);
        double newValue = oldValue + (1 - oldValue) * P_INIT;
        preds.put(host, newValue);
    }

    /**
     * Returns the current prediction (P) value for a host or 0 if entry for the
     * host doesn't exist.
     *
     * @param host The host to look the P for
     * @return the current P value
     */
    public double getPredFor(DTNHost host) {
        ageDeliveryPreds(); // make sure preds are updated before getting
        if (preds.containsKey(host)) {
            return preds.get(host);
        } else {
            return 0;
        }
    }

    /**
     * Updates transitive (A->B->C) delivery predictions.      
     * <CODE> P(a,c) = P(a,c)_old + (1 - P(a,c)_old) * P(a,b) * P(b,c) * BETA </CODE>
     *
     * @param host The B host who we just met
     */
    private void updateTransitivePreds(DTNHost host) {
        MessageRouter otherRouter = host.getRouter();
        assert otherRouter instanceof ProphetRouterFuzzy : "PRoPHET only works "
                + " with other routers of same type";

        double pForHost = getPredFor(host); // P(a,b)
        Map<DTNHost, Double> othersPreds
                = ((ProphetRouterFuzzy) otherRouter).getDeliveryPreds();

        for (Map.Entry<DTNHost, Double> e : othersPreds.entrySet()) {
            if (e.getKey() == getHost()) {
                continue; // don't add yourself
            }

            double pOld = getPredFor(e.getKey()); // P(a,c)_old
            double pNew = pOld + (1 - pOld) * pForHost * e.getValue() * beta;
            preds.put(e.getKey(), pNew);
        }
    }

    /**
     * Ages all entries in the delivery predictions.
     * <CODE>P(a,b) = P(a,b)_old * (GAMMA ^ k)</CODE>, where k is number of time
     * units that have elapsed since the last time the metric was aged.
     *
     * @see #SECONDS_IN_UNIT_S
     */
    private void ageDeliveryPreds() {
        double timeDiff = (SimClock.getTime() - this.lastAgeUpdate)
                / secondsInTimeUnit;

        if (timeDiff == 0) {
            return;
        }

        double mult = Math.pow(GAMMA, timeDiff);
        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            e.setValue(e.getValue() * mult);
        }

        this.lastAgeUpdate = SimClock.getTime();
    }

    /**
     * Returns a map of this router's delivery predictions
     *
     * @return a map of this router's delivery predictions
     */
    private Map<DTNHost, Double> getDeliveryPreds() {
        ageDeliveryPreds(); // make sure the aging is done
        return this.preds;
    }

    /**
     * NEW METHOD ADDED IN BELOW
     *
     */
    protected void setEnergy(double range[]) {
        if (range.length == 1) {
            this.currentEnergy = range[0];
        } else {
            if (rng == null) {
                rng = new Random((int) (range[0] + range[1]));
            }
            this.currentEnergy = range[0] + rng.nextDouble() * (range[1] - range[0]);
        }
    }

    protected void reduceEnergy(double amount) {
        if (SimClock.getTime() < this.warmupTime) {
            return; // Does Nothing
        }

        comBus.updateDouble(ENERGY_VALUE_ID, -amount);
        if (this.currentEnergy < 0) {
            comBus.updateProperty(ENERGY_VALUE_ID, 0.0);
        }
    }

    protected void reduceSendingAndScanningEnergy() {
        double simTime = SimClock.getTime();

        if (this.comBus == null) {
            this.comBus = getHost().getComBus();
            this.comBus.addProperty(ENERGY_VALUE_ID, this.currentEnergy);
            this.comBus.subscribe(ENERGY_VALUE_ID, this);
        }

        if (this.currentEnergy <= 0) {
            /* turn radio off */
            this.comBus.updateProperty(NetworkInterface.RANGE_ID, 0.0);
            return;
            /* no more energy to start new transfers */
        }

        if (simTime > this.lastUpdate && sendingConnections.size() > 0) {
            /* sending data */
            reduceEnergy((simTime - this.lastUpdate) * this.transmitEnergy);
        }
        this.lastUpdate = simTime;

        if (simTime > this.lastScanUpdate) {
            /* scanning at this update round */
            reduceEnergy(this.scanEnergy);
            this.lastScanUpdate = simTime;
        }
    }

    /**
     * Ambil energi saat ini
     *
     * @param h
     * @return
     */
    public double getEnergy(DTNHost h) {
        return (Double) h.getComBus().getProperty(ENERGY_VALUE_ID);
    }

    public int getFreeBufferS(DTNHost host) {
        return host.getRouter().getFreeBufferSize();
    }

    private double Defuzzification(DTNHost nodes) {
        double energyCurrent = getEnergy(nodes);
        double freeBuffer = getFreeBufferS(nodes);
//        freeBuffer--;
//        System.out.println("Test");
        FunctionBlock functionBlock = fcl.getFunctionBlock(null);
        functionBlock.setVariable(CURRENT_ENERGY, energyCurrent);
        functionBlock.setVariable(FREE_BUFFER, freeBuffer);
        functionBlock.evaluate();

        Variable tou = functionBlock.getVariable(TRANSFER_OF_UTILITY);

        return tou.getValue();
    }

    /**
     * STOP
     *
     */
    @Override
    public void update() {
        super.update();
        reduceSendingAndScanningEnergy();

        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring 
        }

        // try messages that could be delivered to final recipient
        if (exchangeDeliverableMessages() != null) {
            return;
        }
        tryOtherMessages();
    }

    /**
     * Tries to send all other messages to all connected hosts ordered by their
     * delivery probability
     *
     * @return The return value of {@link #tryMessagesForConnected(List)}
     */
    private Tuple<Message, Connection> tryOtherMessages() {
        List<Tuple<Message, Connection>> messages = new ArrayList<Tuple<Message, Connection>>();
        // List<Connection> connections = getConnections();
        Collection<Message> msgCollection = getMessageCollection();

        /* for all connected hosts collect all messages that have a higher
		   probability of delivery by the other host */
        for (Connection con : getConnections()) {
            DTNHost other = con.getOtherNode(getHost());
            ProphetRouterFuzzy othRouter = (ProphetRouterFuzzy) other.getRouter();
//            int free_Buffer = othRouter.getFreeBufferSize();

            if (othRouter.isTransferring()) {
//                continue; // skip hosts that are transferring
//            }
                for (Message m : msgCollection) { 
                    DTNHost dest = m.getTo();
//                    ProphetRouterFuzzy othPair = (ProphetRouterFuzzy)m.getTo().getRouter();
                    double me = this.Defuzzification(dest);
                    double peer = othRouter.Defuzzification(dest);
                    int freeBuffers = othRouter.getFreeBufferS(other);
//                    int size = m.getSize();

                    if (othRouter.hasMessage(m.getId())) {
                        continue; // skip messages that the other one has
                    }

                    tryAllMessagesToAllConnections();
                    if (othRouter.getPredFor(m.getTo()) > getPredFor(m.getTo())) {
                        this.connectedHost.add(other);

                        if (me < peer) {
                            if (freeBuffers > 0) {
                                // the other node has higher probability of delivery
                                messages.add(new Tuple<Message, Connection>(m, con));
                                reduceSendingAndScanningEnergy();
                            }
                        }
                        break;
                    }
                }
            }
        }
        if (messages.size() == 0) {
            return null;
        }

        // sort the message-connection tuples
        Collections.sort(messages, new TupleComparator());
        return tryMessagesForConnected(messages);	// try to send messages
    }

    /**
     * Comparator for Message-Connection-Tuples that orders the tuples by their
     * delivery probability by the host on the other side of the connection
     * (GRTRMax)
     */
    private class TupleComparator implements Comparator<Tuple<Message, Connection>> {

        public int compare(Tuple<Message, Connection> tuple1,
                Tuple<Message, Connection> tuple2) {
            // delivery probability of tuple1's message with tuple1's connection
            double p1 = ((ProphetRouterFuzzy) tuple1.getValue().
                    getOtherNode(getHost()).getRouter()).getPredFor(
                    tuple1.getKey().getTo());
            // -"- tuple2...
            double p2 = ((ProphetRouterFuzzy) tuple2.getValue().
                    getOtherNode(getHost()).getRouter()).getPredFor(
                    tuple2.getKey().getTo());

            // bigger probability should come first
            if (p2 - p1 == 0) {
                /* equal probabilities -> let queue mode decide */
                return compareByQueueMode(tuple1.getKey(), tuple2.getKey());
            } else if (p2 - p1 < 0) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    @Override
    public void moduleValueChanged(String key, Object newValue) {
        this.currentEnergy = (Double) newValue;
    }

    @Override
    public RoutingInfo getRoutingInfo() {
        ageDeliveryPreds();
        RoutingInfo top = super.getRoutingInfo();
        RoutingInfo ri = new RoutingInfo(preds.size()
                + " delivery prediction(s)");

        for (Map.Entry<DTNHost, Double> e : preds.entrySet()) {
            DTNHost host = e.getKey();
            Double value = e.getValue();

            ri.addMoreInfo(new RoutingInfo(String.format("%s : %.6f",
                    host, value)));
        }

        top.addMoreInfo(ri);
        return top;
    }

    @Override
    public MessageRouter replicate() {
        ProphetRouterFuzzy r = new ProphetRouterFuzzy(this);
        return r;
    }

//    @Override
//    public Map<DTNHost, double[]> getEnergyPerInterval() {
//        Map<DTNHost, double[]> energyMap = new HashMap<>();
//        energyMap.put(this.getHost(), new double[]{this.currentEnergy});
//        return energyMap;
//    }
}
