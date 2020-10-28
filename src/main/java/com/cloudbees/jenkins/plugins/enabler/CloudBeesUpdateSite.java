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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.UpdateSite;
import org.apache.commons.io.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;

import jenkins.util.JSONSignatureValidator;

/**
 * An update site that uses the CloudBees certificate for signing.
 * Using a custom class name to avoid classpath clashes.
 */
public class CloudBeesUpdateSite extends UpdateSite {
    /**
     * Constructor.
     *
     * @param id  the ID of the update site.
     * @param url the url of the update site.
     */
    public CloudBeesUpdateSite(String id, String url) {
        super(id, url);
    }

    @Override protected JSONSignatureValidator getJsonSignatureValidator(@CheckForNull String name) {
        if (name == null) {
            name = "update site '" + getId() + "'";
        } else if (name.startsWith("downloadable")) {
            // for downloadables we only want to validate those provided by the community
            // so we use the parent validator.
            return super.getJsonSignatureValidator(name);
        }
        return new JSONSignatureValidator(name) {
            @Override protected Set<TrustAnchor> loadTrustAnchors(CertificateFactory cf) throws IOException {
                InputStream stream = CloudBeesUpdateSite.class.getResourceAsStream("/cloudbees-root-cacerts.pem");
                try {
                    if (stream != null) {
                        return ((List<X509Certificate>) cf.generateCertificates(stream)).stream()
                                .map(cert -> new TrustAnchor(cert, null))
                                .collect(Collectors.toSet());
                    } else {
                        return Collections.emptySet();
                    }
                } catch (CertificateException x) {
                    throw new IOException(x);
                } finally {
                    IOUtils.closeQuietly(stream);
                }
            }
        };
    }

}
