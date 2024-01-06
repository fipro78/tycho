/*******************************************************************************
 * Copyright (c) 2024 Christoph Läubrich and others.
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Christoph Läubrich - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.zipcomparator.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.tycho.artifactcomparator.ArtifactComparator.ComparisonData;
import org.eclipse.tycho.artifactcomparator.ArtifactDelta;
import org.eclipse.tycho.artifactcomparator.ComparatorInputStream;

import ch.digitalfondue.jfiveparse.Comment;
import ch.digitalfondue.jfiveparse.Document;
import ch.digitalfondue.jfiveparse.Element;
import ch.digitalfondue.jfiveparse.JFiveParse;
import ch.digitalfondue.jfiveparse.Node;

/**
 * Compares html files for some special cases and fall back to simple textcompare otherwise
 */
@Component(role = ContentsComparator.class, hint = HtmlComparator.HINT)
public class HtmlComparator implements ContentsComparator {

    private static final String META_ELEMENT = "meta";
    private static final String LINK_ELEMENT = "link";
    private static final String SCRIPT_ELEMENT = "script";
    private static final String HEAD_ELEMENT = "head";
    static final String HINT = "html";

    @Override
    public ArtifactDelta getDelta(ComparatorInputStream baseline, ComparatorInputStream reactor, ComparisonData data)
            throws IOException {
        try {
            Document baselineDoc = JFiveParse.parse(baseline.asString(StandardCharsets.UTF_8));
            Document reactorDoc = JFiveParse.parse(reactor.asString(StandardCharsets.UTF_8));
            //TODO we might generally want to remove all comment tags?
            if (isJavadocHtml(baselineDoc)) {
                cleanJavaDoc(baselineDoc.getDocumentElement());
                cleanJavaDoc(reactorDoc.getDocumentElement());
                String serializeBaseline = serializeAndNormalize(baselineDoc);
                String serializeReactor = serializeAndNormalize(reactorDoc);
                if (serializeBaseline.equalsIgnoreCase(serializeReactor)) {
                    return ArtifactDelta.NO_DIFFERENCE;
                }
            } else {
                //TODO compare each element and attributes ...
            }
        } catch (Exception e) {
            //it might not be a valid html5 so don't bother and compare the text directly
        }
        return TextComparator.compareText(baseline, reactor, data);
    }

    private String serializeAndNormalize(Document baselineDoc) {
        return JFiveParse.serialize(baselineDoc).replaceAll("\\s+", " ").replace("\r", "").replace("\n", "")
                .replace("> <", "><");
    }

    private void cleanJavaDoc(Element root) {
        //for javadoc we want to ignore all script and css tags...
        for (Element headElement : root.getElementsByTagName(HEAD_ELEMENT)) {
            for (Node node : headElement.getChildNodes().toArray(Node[]::new)) {
                if (node instanceof Element element) {
                    String nodeName = element.getNodeName();
                    if (SCRIPT_ELEMENT.equalsIgnoreCase(nodeName) || LINK_ELEMENT.equalsIgnoreCase(nodeName)
                            || META_ELEMENT.equals(nodeName)) {
                        headElement.removeChild(node);
                    }
                }
                if (node instanceof Comment comment) {
                    headElement.removeChild(node);
                }
            }
        }
        //don't bother about lang...
        root.removeAttribute("lang");
    }

    private boolean isJavadocHtml(Document doc) {
        List<Element> elementsByTagName = doc.getDocumentElement().getElementsByTagName(HEAD_ELEMENT);
        for (Element element : elementsByTagName) {
            for (Node node : element.getChildNodes()) {
                if (node instanceof Comment comment) {
                    String data = comment.getData();
                    if (data != null && data.trim().toLowerCase().startsWith("generated by javadoc")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean matches(String nameOrExtension) {
        return HINT.equalsIgnoreCase(nameOrExtension) || "htm".equalsIgnoreCase(nameOrExtension);
    }
}
