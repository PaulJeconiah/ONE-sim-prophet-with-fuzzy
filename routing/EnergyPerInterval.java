/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

import core.*;
import java.util.*;
/**
 *
 * @author marto
 */
public interface EnergyPerInterval {
 public Map<DTNHost, double[] > getEnergyPerInterval();
}
