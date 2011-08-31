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

import static org.restlet.data.MediaType.APPLICATION_WWW_FORM;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.restlet.Application;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.LocalReference;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.ext.freemarker.ContextTemplateLoader;
import org.restlet.representation.Representation;
import org.restlet.resource.Directory;
import org.restlet.resource.ResourceException;
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

    private static Configuration createFreeMarkerConfig(Context context) {

        Configuration fmCfg = new Configuration();
        fmCfg.setLocalizedLookup(false);

        LocalReference fmBaseRef = LocalReference.createClapReference("/");
        fmCfg.setTemplateLoader(new ContextTemplateLoader(context, fmBaseRef));

        return fmCfg;
    }

    public static void checkEntity(Representation entity) {
        if (entity == null) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "post with null entity");
        }
    }

    public static void checkMediaType(MediaType mediaType) {
        if (!APPLICATION_WWW_FORM.equals(mediaType, true)) {
            throw new ResourceException(
                    Status.CLIENT_ERROR_UNSUPPORTED_MEDIA_TYPE,
                    mediaType.getName());
        }
    }

    public static String getDateTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = new Date();

        return dateFormat.format(date);
    }

    public static String join(List<String> list, String conjunction) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String item : list) {
            if (first)
                first = false;
            else
                sb.append(conjunction);
            sb.append(item);
        }
        return sb.toString();
    }

    @Override
    public Restlet createInboundRoot() {
        Context context = getContext();

        freeMarkerConfiguration = createFreeMarkerConfig(context);

        setStatusService(new CommonStatusService(freeMarkerConfiguration));

        // The guard is needed although JAAS which is doing the authentication
        // just to be able to retrieve client information (challenger).
        DumpVerifier verifier = new DumpVerifier();
        ChallengeAuthenticator guard = new ChallengeAuthenticator(getContext(),
                ChallengeScheme.HTTP_BASIC,
                "Stratuslab Persistent Disk Storage");
        guard.setVerifier(verifier);

        Router router = new Router(context);

        router.attach("/disks/{uuid}/", DiskResource.class);
        router.attach("/disks/{uuid}", ForceTrailingSlashResource.class);

        router.attach("/disks/", DisksResource.class);
        router.attach("/disks", ForceTrailingSlashResource.class);

        router.attach("/api/{action}/{uuid}", ActionResource.class);
        router.attach("/api/{action}", ActionResource.class);

        // router.attach("/logout/", LogoutResource.class);
        // router.attach("/logout", ForceTrailingSlashResource.class);

        router.attach("/", HomeResource.class);

        Directory cssDir = new Directory(getContext(), "war:///css");
        cssDir.setNegotiatingContent(false);
        cssDir.setIndexName("index.html");
        router.attach("/css/", cssDir);

        guard.setNext(router);

        return guard;
    }

    public Configuration getFreeMarkerConfiguration() {
        return freeMarkerConfiguration;
    }

    public static <T> T last(T[] array) {
        return array[array.length - 1];
    }

}
