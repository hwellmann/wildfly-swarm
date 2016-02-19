/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.swarmtool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import static java.util.Arrays.asList;

public class Main {


    private static final OptionParser OPT_PARSER = new OptionParser();

    private static final OptionSpec<Void> HELP_OPT =
            OPT_PARSER.acceptsAll(asList("h", "help"), "print help and exit")
                    .forHelp();

    private static final OptionSpec<Void> VERSION_OPT =
            OPT_PARSER.acceptsAll(asList("v", "version"), "print version and exit")
                    .forHelp();

    private static final OptionSpec<Void> DISABLE_AUTO_DETECT =
            OPT_PARSER.accepts("no-fraction-detect", "disable auto fraction detection");

    private static final OptionSpec<String> FRACTIONS_OPT =
            OPT_PARSER.acceptsAll(asList("f", "fractions"), "swarm fractions to include")
                    .withRequiredArg()
                    .ofType(String.class)
                    .withValuesSeparatedBy(',')
                    .describedAs("undertow,jaxrs,...");

    private static final OptionSpec<String> OUTPUT_DIR_OPT =
            OPT_PARSER.acceptsAll(asList("o", "output-dir"), "directory where the final jar will be written")
                    .withRequiredArg()
                    .ofType(String.class)
                    .defaultsTo(".")
                    .describedAs("path");

    private static final OptionSpec<String> NAME_OPT =
            OPT_PARSER.acceptsAll(asList("n", "name"), "The name of the final jar sans the -swarm.jar suffix (default: <source name>)")
                    .withRequiredArg()
                    .ofType(String.class)
                    .describedAs("jar-name");

    private static final OptionSpec<File> SOURCE_OPT =
            OPT_PARSER.nonOptions("The source artifact")
                    .ofType(File.class);

    private static final OptionSpec<String> SYSPROPS_OPT =
            OPT_PARSER.accepts("D", "system property (overrides entry in --property-file)")
                    .withRequiredArg()
                    .ofType(String.class)
                    .describedAs("key=value");

    private static final OptionSpec<File> SYSPROPS_FILE_OPT =
            OPT_PARSER.accepts("property-file", "system properties")
                    .withRequiredArg()
                    .ofType(File.class)
                    .describedAs("system properties file");

    private static final String VERSION;

    static {
        Properties props = new Properties();
        try (InputStream propStream = Main.class.getClassLoader()
                .getResourceAsStream("org/wildfly/swarm/swarmtool/version.properties")) {
            props.load(propStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        VERSION = props.getProperty("version");
    }

    public static void main(final String[] args) throws Exception {
        try {
            generateSwarmJar(args);
        } catch (ExitException e) {
            final String msg = e.getMessage();
            if (msg != null) {
                System.err.println(msg);
            }

            System.exit(e.status);
        }
    }

    protected static File generateSwarmJar(final String[] args) throws Exception {
        OptionSet foundOptions = null;

        try {
            foundOptions = OPT_PARSER.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage() + "\n");
        }

        if (foundOptions == null ||
                foundOptions.has(HELP_OPT)) {
            OPT_PARSER.printHelpOn(System.err);

            exit(null);
        }

        if (foundOptions.has(VERSION_OPT)) {
            exit("swarmtool v" + VERSION, 0);
        }

        final List<File> nonOptArgs = foundOptions.valuesOf(SOURCE_OPT);
        if (nonOptArgs.isEmpty()) {
            exit("No source artifact specified.");
        }
        if (nonOptArgs.size() > 1) {
            exit("Too many source artifacts provided (" + nonOptArgs + ")");
        }

        final File source = nonOptArgs.get(0);
        if (!source.exists()) {
            exit("File " + source.getAbsolutePath() + " does not exist.");
        }

        final Properties properties = new Properties();
        if (foundOptions.has(SYSPROPS_FILE_OPT)) {
            try (InputStream in = new FileInputStream(foundOptions.valueOf(SYSPROPS_FILE_OPT))) {
                properties.load(in);
            }
        }

        foundOptions.valuesOf(SYSPROPS_OPT)
                .forEach(prop -> {
                    final String[] parts = prop.split("=");
                    properties.put(parts[0], parts[1]);
                });

        return new Build()
                .source(source)
                .swarmVersion(VERSION)
                .addSwarmDependencies(foundOptions.valuesOf(FRACTIONS_OPT))
                .outputDir(new File(foundOptions.valueOf(OUTPUT_DIR_OPT)))
                .name(foundOptions.valueOf(NAME_OPT))
                .autoDetectFractions(!foundOptions.has(DISABLE_AUTO_DETECT))
                .properties(properties)
                .run();
    }

    private static void exit(String message) {
        exit(message, 1);
    }

    private static void exit(String message, int code) {
        throw new ExitException(code, message);
    }

    static class ExitException extends RuntimeException {
        public int status;

        ExitException(final int status, final String message) {
            super(message);
            this.status = status;
        }
    }
}
