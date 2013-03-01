package org.drooms.strategy.runaway;

import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderConfiguration;
import org.drools.builder.KnowledgeBuilderError;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.io.ResourceFactory;
import org.drooms.api.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a simple run away strategy.
 */
public class RunAwayStrategy implements Strategy {

    private static final Logger LOG = LoggerFactory.getLogger(RunAwayStrategy.class);

    public RunAwayStrategy() {
        // do nothing
    }

    @Override
    public KnowledgeBuilder getKnowledgeBuilder(final ClassLoader cls) {
        final KnowledgeBuilderConfiguration conf = KnowledgeBuilderFactory
                .newKnowledgeBuilderConfiguration(null, cls);
        final KnowledgeBuilder kb = KnowledgeBuilderFactory
                .newKnowledgeBuilder(conf);
        kb.add(ResourceFactory.newClassPathResource("basic_move_rules.drl",
                cls), ResourceType.DRL);

        if (kb.hasErrors()) {
            for (KnowledgeBuilderError error : kb.getErrors()) {
                LOG.warn(error.getMessage() + ": " + error.getResource());
            }
        }
        return kb;
    }

    @Override
    public String getName() {
        return "Run Away!";
    }

    @Override
    public boolean enableAudit() {
        return true;
    }
}
