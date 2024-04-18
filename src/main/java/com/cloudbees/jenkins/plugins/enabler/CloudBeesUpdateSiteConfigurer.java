/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.enabler;

import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.DownloadService;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.util.PersistedList;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adds the custom update site.
 */
public final class CloudBeesUpdateSiteConfigurer {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(CloudBeesUpdateSiteConfigurer.class.getName());

    private static final Map<String, UpdateCenterInfo> ucs;

    static {
        ucs = new HashMap<>();
        UpdateCenterInfo uc;

        uc = new UpdateCenterInfo("cloudbees-platform-insights", "CloudBees Platform Insights", "https://jenkins-updates.cloudbees.com/update-center/cloudbees-platform-insights/update-center.json");
        ucs.put(uc.getId(), uc);
    }

    /** Constructor. */
    public CloudBeesUpdateSiteConfigurer() {
    }

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)
    public static void configureUpdateCenter() throws Exception {
        LOGGER.log(Level.FINE, "Checking whether CloudBees Update centers are configured");
        UpdateCenter updateCenter = Jenkins.get().getUpdateCenter();
        boolean changed = false;
        List<CloudBeesUpdateSite> added = new ArrayList<>();
        synchronized (updateCenter) {
            PersistedList<UpdateSite> sites = updateCenter.getSites();
            if (sites.isEmpty()) {
                // likely the list has not been loaded yet
                updateCenter.load();
                sites = updateCenter.getSites();
            }
            List<UpdateSite> newList = new ArrayList<>();
            for (UpdateSite site : sites) {
                if (site instanceof CloudBeesUpdateSite) {
                    UpdateCenterInfo ucInfo = ucs.get(site.getId());
                    if (ucInfo != null && !ucInfo.isFound() && ucInfo.getUrl().equals(site.getUrl())) {
                        // We have found it and is correct, we keep it in the list
                        LOGGER.log(Level.FINEST, ucInfo.getDisplayName() + " Update center already configured");
                        ucInfo.found();
                        newList.add(site);
                    } else {
                        // We already have the correct one or it is incorrect. We skip it.
                        LOGGER.log(Level.FINEST, "Removing extra/invalid " + site.getId() + " Update center");
                        changed = true;
                    }
                } else {
                    newList.add(site);
                }
            }
            // Add UCs not already there
            ucs.values().stream()
                    .filter(updateCenterInfo -> !updateCenterInfo.isFound())
                    .forEach(ucInfo -> {
                LOGGER.log(Level.INFO, "Adding " + ucInfo.getDisplayName() + " Update center");
                CloudBeesUpdateSite uc = new CloudBeesUpdateSite(ucInfo.getId(), ucInfo.getUrl());
                added.add(uc);
                newList.add(uc);
            });
            changed |= !added.isEmpty();
            // If we have changed anything we update the list
            if (changed) {
                LOGGER.log(Level.FINEST, "Reconfiguring update sites");
                sites.replaceBy(newList);
                for (CloudBeesUpdateSite uc : added) {
                    uc.updateDirectly(DownloadService.signatureCheck);
                }
            } else {
                LOGGER.log(Level.FINEST, "No update site reconfiguration needed");
            }
        }
    }

    private static class UpdateCenterInfo {
        private final String id;
        private final String displayName;
        private final String url;
        private boolean found;

        public UpdateCenterInfo(String id, String displayName, String url) {
            this.id = id;
            this.displayName = displayName;
            this.url = url;
        }

        public String getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getUrl() {
            return url;
        }

        public void found() {
            found = true;
        }

        public boolean isFound() {
            return found;
        }

    }
}
