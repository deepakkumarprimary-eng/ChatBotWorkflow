package com.xpressbees.chatbot.util;

import java.util.List;
import java.util.Map;

/**
 * Pure static utility class for workflow JSON node resolution.
 * No dependencies, no mutable state.
 */
@SuppressWarnings("unchecked")
public final class WorkflowJsonUtils {

    private WorkflowJsonUtils() {
        // Prevent instantiation
    }

    /**
     * Finds the target node for a given source node via the first matching transition.
     *
     * @param currentNodeId the source node ID to find a transition from
     * @param workflowJson  the workflow JSON map containing "nodes" and "transitions"
     * @return the target node map, or null if no transition exists
     */
    public static Map<String, Object> resolveNextNode(String currentNodeId, Map<String, Object> workflowJson) {
        if (currentNodeId == null || workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");

        if (transitions == null || nodes == null) {
            return null;
        }

        for (Map<String, Object> transition : transitions) {
            if (currentNodeId.equals(transition.get("sourceNodeId"))) {
                String targetNodeId = (String) transition.get("targetNodeId");
                for (Map<String, Object> n : nodes) {
                    if (targetNodeId.equals(n.get("id"))) {
                        return n;
                    }
                }
                break;
            }
        }
        return null;
    }

    /**
     * Finds a specific target node by its ID (for conditional/targeted routing).
     * Falls back to resolveNextNode(currentNodeId, workflowJson) if targetNodeId is null.
     *
     * @param currentNodeId the current node ID (used for fallback)
     * @param targetNodeId  the specific target node ID to find
     * @param workflowJson  the workflow JSON map containing "nodes" and "transitions"
     * @return the target node map, or null if not found
     */
    public static Map<String, Object> resolveNextNode(String currentNodeId, String targetNodeId, Map<String, Object> workflowJson) {
        if (workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
        if (nodes == null || targetNodeId == null) {
            return resolveNextNode(currentNodeId, workflowJson);
        }

        for (Map<String, Object> node : nodes) {
            if (targetNodeId.equals(node.get("id"))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Returns the first node in a workflow based on the first transition's sourceNodeId.
     *
     * @param workflowJson the workflow JSON map containing "nodes" and "transitions"
     * @return the first node map, or null if workflow has no transitions
     */
    public static Map<String, Object> findFirstNode(Map<String, Object> workflowJson) {
        if (workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");

        if (transitions == null || transitions.isEmpty() || nodes == null) {
            return null;
        }

        String firstNodeId = (String) transitions.get(0).get("sourceNodeId");
        for (Map<String, Object> node : nodes) {
            if (firstNodeId.equals(node.get("id"))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Locates a node by its ID within the workflow's nodes array.
     *
     * @param nodeId       the node ID to search for
     * @param workflowJson the workflow JSON map containing "nodes"
     * @return the node map, or null if not found
     */
    public static Map<String, Object> findNodeById(String nodeId, Map<String, Object> workflowJson) {
        if (nodeId == null || workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");
        if (nodes == null) {
            return null;
        }

        for (Map<String, Object> node : nodes) {
            if (nodeId.equals(node.get("id"))) {
                return node;
            }
        }
        return null;
    }

    /**
     * Finds a target node connected to currentNodeId whose name matches targetName.
     *
     * @param currentNodeId the source node ID to find transitions from
     * @param targetName    the name to match on the target node
     * @param workflowJson  the workflow JSON map containing "nodes" and "transitions"
     * @return the matching target node map, or null if no match
     */
    public static Map<String, Object> findTargetNodeByName(String currentNodeId, String targetName, Map<String, Object> workflowJson) {
        if (currentNodeId == null || targetName == null || workflowJson == null) {
            return null;
        }

        List<Map<String, Object>> transitions = (List<Map<String, Object>>) workflowJson.get("transitions");
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) workflowJson.get("nodes");

        if (transitions == null || nodes == null) {
            return null;
        }

        for (Map<String, Object> transition : transitions) {
            if (currentNodeId.equals(transition.get("sourceNodeId"))) {
                String targetNodeId = (String) transition.get("targetNodeId");
                for (Map<String, Object> node : nodes) {
                    if (targetNodeId.equals(node.get("id"))) {
                        Object name = node.get("name");
                        if (name != null && targetName.equals(String.valueOf(name))) {
                            return node;
                        }
                        break;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Extracts the nodeType from a node's config map.
     *
     * @param node the node map containing a "config" entry
     * @return the nodeType string, or null if not present
     */
    public static String extractNodeType(Map<String, Object> node) {
        if (node == null) {
            return null;
        }

        Map<String, Object> config = (Map<String, Object>) node.get("config");
        if (config != null && config.get("nodeType") != null) {
            return (String) config.get("nodeType");
        }
        return null;
    }
}
