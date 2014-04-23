package io.lumify.web.routes.relationship;

import io.lumify.core.config.Configuration;
import io.lumify.core.model.audit.AuditAction;
import io.lumify.core.model.audit.AuditRepository;
import io.lumify.core.model.ontology.OntologyProperty;
import io.lumify.core.model.ontology.OntologyRepository;
import io.lumify.core.model.user.UserRepository;
import io.lumify.core.model.workQueue.WorkQueueRepository;
import io.lumify.core.security.LumifyVisibility;
import io.lumify.core.user.User;
import io.lumify.core.util.JsonSerializer;
import io.lumify.web.BaseRequestHandler;
import com.altamiracorp.miniweb.HandlerChain;
import com.altamiracorp.securegraph.Authorizations;
import com.altamiracorp.securegraph.Edge;
import com.altamiracorp.securegraph.Graph;
import com.altamiracorp.securegraph.Property;
import com.google.inject.Inject;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

public class DeleteRelationshipProperty extends BaseRequestHandler {
    private final Graph graph;
    private final OntologyRepository ontologyRepository;
    private final AuditRepository auditRepository;
    private final WorkQueueRepository workQueueRepository;

    @Inject
    public DeleteRelationshipProperty(
            final OntologyRepository ontologyRepository,
            final Graph graph,
            final AuditRepository auditRepository,
            final UserRepository userRepository,
            final Configuration configuration,
            final WorkQueueRepository workQueueRepository) {
        super(userRepository, configuration);
        this.ontologyRepository = ontologyRepository;
        this.graph = graph;
        this.auditRepository = auditRepository;
        this.workQueueRepository = workQueueRepository;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, HandlerChain chain) throws Exception {
        LumifyVisibility lumifyVisibility = new LumifyVisibility();
        final String propertyName = getRequiredParameter(request, "propertyName");
        final String sourceId = getRequiredParameter(request, "source");
        final String destId = getRequiredParameter(request, "dest");
        final String edgeId = getRequiredParameter(request, "edgeId");

        User user = getUser(request);
        Authorizations authorizations = getAuthorizations(request, user);
        String workspaceId = getActiveWorkspaceId(request);

        OntologyProperty property = ontologyRepository.getProperty(propertyName);
        if (property == null) {
            throw new RuntimeException("Could not find property: " + propertyName);
        }

        // TODO remove all properties from all edges? I don't think so
        Edge edge = graph.getEdge(edgeId, authorizations);
        Object oldValue = edge.getPropertyValue(propertyName, 0);
        // TODO: replace "" when we implement commenting on ui
        auditRepository.auditRelationshipProperty(AuditAction.DELETE, sourceId, destId, property.getDisplayName(),
                oldValue, null, edge, "", "", user, lumifyVisibility.getVisibility());
        edge.removeProperty(propertyName);
        graph.flush();

        List<Property> properties = new ArrayList<Property>();
        for (Property p : edge.getProperties()) {
            properties.add(p);
        }

        workQueueRepository.pushGraphPropertyQueue(edge, null, propertyName);

        JSONObject resultsJson = JsonSerializer.toJsonProperties(properties, workspaceId);
        respondWithJson(response, resultsJson);
    }
}