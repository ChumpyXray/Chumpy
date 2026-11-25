package slop.miho.chumpy.core;

import slop.miho.chumpy.core.IDelayResult;

oneway interface ILatencyProbe {
    void measure(String configJson, String testUrl, IDelayResult result);
}
