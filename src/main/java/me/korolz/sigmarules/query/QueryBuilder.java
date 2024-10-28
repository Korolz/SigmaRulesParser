package me.korolz.sigmarules.query;

import me.korolz.sigmarules.models.ConditionsManager;
import me.korolz.sigmarules.models.DetectionsManager;
import me.korolz.sigmarules.models.LogSource;
import me.korolz.sigmarules.models.SigmaRule;

public class QueryBuilder {
    private String query = "";
    private LogSource logsource = new LogSource();
    private DetectionsManager detectionsManager = new DetectionsManager();
    private ConditionsManager conditionsManager = new ConditionsManager();

    public QueryBuilder(SigmaRule sigmaRule) {
        logsource = sigmaRule.getLogsource();
    }

    public void buildQuery() {

    }
}
