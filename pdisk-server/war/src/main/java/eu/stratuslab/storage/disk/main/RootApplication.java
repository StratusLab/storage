/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.main;

import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.LocalReference;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;
import org.restlet.security.ChallengeAuthenticator;

import eu.stratuslab.storage.disk.resources.ActionResource;
import eu.stratuslab.storage.disk.resources.DiskResource;
import eu.stratuslab.storage.disk.resources.DisksResource;
import eu.stratuslab.storage.disk.resources.ForceTrailingSlashResource;
import eu.stratuslab.storage.disk.resources.HomeResource;
import eu.stratuslab.storage.disk.utils.DumpVerifier;
import freemarker.template.Configuration;

public class RootApplication extends Application {

    public static final ServiceConfiguration CONFIGURATION = ServiceConfiguration
            .getInstance();

    private Configuration freeMarkerConfiguration = null;

    public RootApplication() {
        setName("StratusLab Persistent Disk Server");
        setDescription("StratusLab server for persistent disk storage.");
        setOwner("StratusLab");
        setAuthor("Charles Loomis");

        getTunnelService().setUserAgentTunnel(true);
    }

    @Override
    public Restlet createInboundRoot() {
        Context context = getContext();

        freeMarkerConfiguration = createFreeMarkerConfig(context);

        setStatusService(new CommonStatusService(freeMarkerConfiguration));

        Router router = new Router(context);

        router.attach("/disks/{uuid}/", DiskResource.class);
        router.attach("/disks/{uuid}", ForceTrailingSlashResource.class);

        router.attach("/disks/", DisksResource.class);
        router.attach("/disks", ForceTrailingSlashResource.class);

        router.attach("/api/{action}/{uuid}", ActionResource.class);
        router.attach("/api/{action}", ActionResource.class);

        router.attach("/", HomeResource.class);

        router.attach("/css/", createCssDirectory(context));

        return createGuard(context, router);
    }

    private static Directory createCssDirectory(Context context) {
        Directory cssDir = new Directory(context, "war:///css");
        cssDir.setNegotiatingContent(false);
        cssDir.setIndexName("index.html");

        return cssDir;
    }

    //
    // This guard is needed although JAAS is doing all of the
    // authentication. This allows the authentication information
    // to be retrieved from the request through the challenge
    // request.
    //
    private static ChallengeAuthenticator createGuard(Context context,
            Router next) {
        DumpVerifier verifier = new DumpVerifier();
        ChallengeAuthenticator guard = new ChallengeAuthenticator(context,
                ChallengeScheme.HTTP_BASIC,
                "Stratuslab Persistent Disk Storage");
        guard.setVerifier(verifier);
        guard.setNext(next);
        return guard;
    }

    private static Configuration createFreeMarkerConfig(Context context) {

        Configuration fmCfg = new Configuration();
        fmCfg.setLocalizedLookup(false);

        LocalReference fmBaseRef = LocalReference.createClapReference("/");
        fmCfg.setTemplateLoader(new ContextTemplateLoader(context, fmBaseRef));

        return fmCfg;
    }

    public Configuration getFreeMarkerConfiguration() {
        return freeMarkerConfiguration;
    }

}
