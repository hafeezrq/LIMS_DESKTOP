package com.qdc.lims.ui.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for closing the current embedded tab when present, or falling back
 * to closing the current window.
 */
public final class ViewCloseUtil {

    private ViewCloseUtil() {
    }

    public static boolean closeCurrentTabOrWindow(Node contentNode) {
        if (contentNode == null || contentNode.getScene() == null || contentNode.getScene().getRoot() == null) {
            return false;
        }

        if (closeContainingTab(contentNode)) {
            return true;
        }

        if (contentNode.getScene().getWindow() instanceof Stage stage) {
            stage.close();
            return true;
        }
        return false;
    }

    private static boolean closeContainingTab(Node contentNode) {
        List<TabPane> tabPanes = new ArrayList<>();
        collectTabPanes(contentNode.getScene().getRoot(), tabPanes);

        TabPane bestPane = null;
        Tab bestTab = null;
        int bestDistance = Integer.MAX_VALUE;

        for (TabPane tabPane : tabPanes) {
            for (Tab tab : tabPane.getTabs()) {
                Node tabContent = tab.getContent();
                int distance = ancestorDistance(contentNode, tabContent);
                if (distance >= 0 && distance < bestDistance) {
                    bestDistance = distance;
                    bestPane = tabPane;
                    bestTab = tab;
                }
            }
        }

        if (bestPane != null && bestTab != null) {
            bestPane.getTabs().remove(bestTab);
            return true;
        }
        return false;
    }

    private static void collectTabPanes(Node node, List<TabPane> result) {
        if (node == null) {
            return;
        }
        if (node instanceof TabPane pane) {
            result.add(pane);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTabPanes(child, result);
            }
        }
    }

    private static int ancestorDistance(Node node, Node potentialAncestor) {
        if (node == null || potentialAncestor == null) {
            return -1;
        }
        if (node == potentialAncestor) {
            return 0;
        }

        int distance = 1;
        Parent current = node.getParent();
        while (current != null) {
            if (current == potentialAncestor) {
                return distance;
            }
            current = current.getParent();
            distance++;
        }
        return -1;
    }
}
