#!/bin/sh

java -agentpath:/usr/local/jprofiler8/bin/linux-x64/libjprofilerti.so -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Dlog4j.defaultInitOverride=true -Dorg.jboss.logging.Logger.pluginClass=org.jboss.logging.logmanager.LoggerPluginImpl -Dtest.dir=. -classpath build/core/test:target/ironjacamar-common-api.jar:target/ironjacamar-common-impl.jar:target/ironjacamar-common-spi.jar:target/ironjacamar-core-api.jar:target/ironjacamar-core-impl.jar:target/ironjacamar-deployers-common.jar:target/ironjacamar-deployers-fungal.jar:target/ironjacamar-embedded.jar:target/ironjacamar-spec-api.jar:target/ironjacamar-validator.jar:lib/embedded/shrinkwrap-api.jar:lib/embedded/shrinkwrap-descriptors-api-base.jar:lib/embedded/shrinkwrap-descriptors-impl-base.jar:lib/embedded/shrinkwrap-descriptors-spi.jar:lib/embedded/shrinkwrap-impl-base.jar:lib/embedded/shrinkwrap-spi.jar:lib/common/classmate.jar:lib/common/commons-logging.jar:lib/common/hibernate-validator.jar:lib/common/jandex.jar:lib/common/jboss-common-core.jar:lib/common/jboss-jaspi-api_1.0_spec.jar:lib/common/jboss-logging.jar:lib/common/jboss-logging-processor.jar:lib/common/jboss-logmanager.jar:lib/common/jboss-stdio.jar:lib/common/jboss-threads.jar:lib/common/jboss-transaction-api_1.2_spec.jar:lib/common/jboss-transaction-spi.jar:lib/common/jcl-over-slf4j.jar:lib/common/jdeparser.jar:lib/common/jgroups.jar:lib/common/jnpserver.jar:lib/common/juel-api.jar:lib/common/juel-impl.jar:lib/common/juel-spi.jar:lib/common/log4j.jar:lib/common/log4j-jboss-logmanager.jar:lib/common/narayana-jta.jar:lib/common/narayana-jts-integration.jar:lib/common/picketbox.jar:lib/common/slf4j-api.jar:lib/common/slf4j-jboss-logmanager.jar:lib/common/txframework.jar:lib/common/validation-api.jar:lib/test/junit.jar:lib/sjc/fungal.jar org.jboss.jca.core.tx.perf.NarayanaMemPerfTestCase $*
