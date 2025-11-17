package com.innovate.tools.ccbuild;

import picocli.CommandLine;

import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;

public class Commands {

    static abstract class Base implements Callable<Integer> {
        @CommandLine.Option(names = "--base", description = "API Base URL (default env CC_API_BASE)")
        String base;

        @CommandLine.Option(names = "--token", description = "API Token (default env CC_API_TOKEN)")
        String token;

        @CommandLine.Option(names = "--subscription", description = "Subscription Code (default env CC_SUBSCRIPTION)")
        String subscription;

        @CommandLine.Option(names = "--builds-path", description = "Builds collection path (default env CC_BUILDS_PATH or /builds)")
        String buildsPath;

        @CommandLine.Option(names = "--delete-template", description = "Delete path template with %s for buildCode (default env CC_DELETE_TEMPLATE or /builds/%s)")
        String deleteTemplate;

        @CommandLine.Option(names = "--page-size", description = "Page size for listing (default 100)")
        int pageSize = 100;

        Client client;

        void init() {
            String b = Util.coalesce(base, System.getenv("CC_API_BASE"));
            String t = Util.coalesce(token, System.getenv("CC_API_TOKEN"));
            String s = Util.coalesce(subscription, System.getenv("CC_SUBSCRIPTION"));
            String bp = Util.coalesce(buildsPath, System.getenv("CC_BUILDS_PATH"), "/builds");
            String dt = Util.coalesce(deleteTemplate, System.getenv("CC_DELETE_TEMPLATE"), "/builds/%s");

            if (Util.isBlank(b) || Util.isBlank(t) || Util.isBlank(s)) {
                throw new IllegalArgumentException("CC_API_BASE, CC_API_TOKEN und CC_SUBSCRIPTION müssen gesetzt sein (Flags oder ENV).");
            }
            this.client = new Client(b, t, s, bp, dt);
        }

        @Override
        public Integer call() throws Exception {
            return 0;
        }
    }

    @CommandLine.Command(name = "list", description = "Builds auflisten (optional filtern & sortieren)")
    public static class ListCmd extends Base {
        @CommandLine.Option(names = "--older-than", description = "Nur Builds älter als Dauer, z.B. 30d, 8w, 1y")
        String olderThan;

        @CommandLine.Option(names = "--limit", description = "Max. Anzahl auszugeben (0 = alle)")
        int limit = 0;

        @Override
        public Integer call() throws Exception {
            init();
            Duration cutoff = Util.parseDuration(olderThan);
            Instant cutoffInstant = cutoff == null ? null : Instant.now().minus(cutoff);

            List<Models.Build> builds = client.listAllBuilds(pageSize);
            builds = Util.applyFilters(builds, cutoffInstant);
            builds.sort(Comparator.comparing(Models.Build::createdAt));

            if (limit > 0 && builds.size() > limit) {
                builds = builds.subList(0, limit);
            }

            Util.printTable(builds);
            return 0;
        }
    }

    @CommandLine.Command(name = "prune", description = "Die N ältesten löschbaren Builds löschen (Dry-Run standardmäßig)")
    public static class PruneCmd extends Base {
        @CommandLine.Option(names = "--older-than", description = "Nur Builds älter als Dauer, z.B. 30d, 8w, 1y")
        String olderThan = "30d";

        @CommandLine.Option(names = "--limit", description = "Wie viele löschen (Default 10)")
        int limit = 10;

        @CommandLine.Option(names = "--keep-latest", description = "Schützt die neuesten N Builds (pro Env)")
        int keepLatest = 5;

        @CommandLine.Option(names = "--include-pinned", description = "Pinned Builds nicht ausschließen (Standard: ausschließen)")
        boolean includePinned = false;

        @CommandLine.Option(names = "--execute", description = "Ohne dieses Flag ist es Dry-Run")
        boolean execute = false;

        @Override
        public Integer call() throws Exception {
            init();
            Duration cutoff = Util.parseDuration(olderThan);
            Instant cutoffInstant = cutoff == null ? null : Instant.now().minus(cutoff);

            List<Models.Build> builds = client.listAllBuilds(pageSize);
            // nur nach Alter filtern
            builds = Util.applyFilters(builds, cutoffInstant);
            // Pinned ausschließen, wenn nicht explizit gewünscht
            if (!includePinned) {
                builds.removeIf(Models.Build::pinned);
            }
            // nur „deletable“ Status
            //builds.removeIf(b -> !Util.isDeletableStatus(b.status()));
            // Nach Alter sortieren
            //builds.sort(Comparator.comparing(Models.Build::createdAt));
            // die neuesten N schützen
            if (keepLatest > 0 && builds.size() > keepLatest) {
                builds = new ArrayList<>(builds.subList(keepLatest, builds.size()));
            }
            // auf Limit kürzen
            if (limit > 0 && builds.size() > limit) {
                //builds = builds.subList(0, limit);
                // starting with the oldest
                builds = builds.subList(builds.size() - limit, builds.size());
            }

            System.out.println((execute ? "[PRUNE] Löschen" : "[DRY-RUN] Würde löschen") + ": " + builds.size() + " Builds\n");
            Util.printTable(builds);

            if (execute) {
                for (Models.Build b : builds) {
                    try {
                        client.deleteBuild(b.code());
                        System.out.printf("✔ scheduled deletion: %s (%s)\n", b.code(), b.name());
                    } catch (Exception ex) {
                        System.err.printf("✖ failed: %s (%s) -> %s\n", b.code(), b.name(), ex.getMessage());
                    }
                }
            }
            return 0;
        }
    }
}
