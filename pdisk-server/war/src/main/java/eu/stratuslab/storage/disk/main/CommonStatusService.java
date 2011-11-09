package eu.stratuslab.storage.disk.main;

import static org.restlet.data.MediaType.APPLICATION_JSON;
import static org.restlet.data.MediaType.APPLICATION_XHTML;
import static org.restlet.data.MediaType.TEXT_HTML;
import static org.restlet.data.MediaType.TEXT_PLAIN;

import java.util.List;
import java.util.Map;

import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ClientInfo;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.service.StatusService;

import eu.stratuslab.storage.disk.resources.BaseResource;
import freemarker.template.Configuration;

public class CommonStatusService extends StatusService {

    @Override
    public Representation getRepresentation(Status status, Request request,
            Response response) {

        ClientInfo clientInfo = request.getClientInfo();
        List<Preference<MediaType>> mediaTypes = clientInfo
                .getAcceptedMediaTypes();

        Map<String, Object> info = getErrorInfo(status, request);

        Configuration freeMarkerConfiguration = BaseResource
                .extractFmConfiguration(request);

        if (freeMarkerConfiguration == null) {
            return null;
        }

        for (Preference<MediaType> preference : mediaTypes) {

            MediaType desiredMediaType = preference.getMetadata();

            if (TEXT_HTML.isCompatible(desiredMediaType)) {

                return toHtml(freeMarkerConfiguration, info);

            } else if (APPLICATION_XHTML.isCompatible(desiredMediaType)) {

                return toHtml(freeMarkerConfiguration, info);

            } else if (TEXT_PLAIN.isCompatible(desiredMediaType)) {

                return toText(freeMarkerConfiguration, info);

            } else if (APPLICATION_JSON.isCompatible(desiredMediaType)) {

                return toJson(freeMarkerConfiguration, info);

            }
        }

        return toText(freeMarkerConfiguration, info);
    }

    private Representation toText(Configuration freeMarkerConfiguration,
            Map<String, Object> info) {
        return BaseResource.createTemplateRepresentation(
                freeMarkerConfiguration, "text/error.ftl", info, TEXT_PLAIN);
    }

    private Representation toJson(Configuration freeMarkerConfiguration,
            Map<String, Object> info) {
        return BaseResource.createTemplateRepresentation(
                freeMarkerConfiguration, "json/error.ftl", info,
                APPLICATION_JSON);
    }

    private Representation toHtml(Configuration freeMarkerConfiguration,
            Map<String, Object> info) {
        return BaseResource.createTemplateRepresentation(
                freeMarkerConfiguration, "html/error.ftl", info, TEXT_HTML);
    }

    private static Map<String, Object> getErrorInfo(Status status,
            Request request) {

        Map<String, Object> info = BaseResource.createInfoStructure("Error",
                request, BaseResource.getBaseUrl(request));

        info.put("errorMsg", status.getDescription());
        info.put("errorCode", status.getCode());

        return info;
    }

}
