package com.psddev.cms.tool.page;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.ImmutableMap;
import com.psddev.cms.db.Content;
import com.psddev.cms.db.Draft;
import com.psddev.cms.db.History;
import com.psddev.cms.db.Schedule;
import com.psddev.cms.db.ToolUi;
import com.psddev.cms.tool.CmsTool;
import com.psddev.cms.tool.Search;
import com.psddev.cms.tool.ToolPageContext;
import com.psddev.cms.tool.Widget;
import com.psddev.dari.db.ObjectType;
import com.psddev.dari.db.Query;
import com.psddev.dari.db.State;
import com.psddev.dari.util.JspUtils;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.PaginatedResult;

public class ContentRevisions extends Widget {

    {
        setDisplayName("Revisions");
        setInternalName("cms.contentRevision");
        addPosition(CmsTool.CONTENT_RIGHT_WIDGET_POSITION, 0, 3);
    }

    @Override
    public boolean shouldDisplayInNonPublishable() {
        return true;
    }

    @Override
    public String createDisplayHtml(ToolPageContext page, Object object) throws IOException {
        Writer oldDelegate = page.getDelegate();
        StringWriter newDelegate = new StringWriter();

        try {
            page.setDelegate(newDelegate);
            writeDisplayHtml(page, object);
            return newDelegate.toString();

        } finally {
            page.setDelegate(oldDelegate);
        }
    }

    private void writeDisplayHtml(ToolPageContext page, Object object) throws IOException {
        State state = State.getInstance(object);

        if (state.isNew()) {
            return;
        }

        List<Draft> scheduled = new ArrayList<Draft>();
        List<Draft> drafts = new ArrayList<Draft>();
        List<History> namedHistories = new ArrayList<History>();
        List<History> histories = new ArrayList<History>();

        Object selected = page.getOverlaidHistory(object);

        if (selected == null) {
            selected = page.getOverlaidDraft(object);

            if (selected == null) {
                selected = object;
            }
        }

        for (Draft d : Query
                .from(Draft.class)
                .where("objectId = ?", state.getId())
                .selectAll()) {
            if (d.getSchedule() != null) {
                scheduled.add(d);

            } else {
                drafts.add(d);
            }
        }

        Collections.sort(scheduled, new Comparator<Draft>() {
            @Override
            public int compare(Draft x, Draft y) {
                return ObjectUtils.compare(x.getSchedule().getTriggerDate(), y.getSchedule().getTriggerDate(), true);
            }
        });

        Collections.sort(drafts, new Comparator<Draft>() {
            @Override
            public int compare(Draft x, Draft y) {
                return ObjectUtils.compare(
                        x.as(Content.ObjectModification.class).getUpdateDate(),
                        y.as(Content.ObjectModification.class).getUpdateDate(),
                        true);
            }
        });

        for (History h : Query
                .from(History.class)
                .where("name != missing and objectId = ?", state.getId())
                .sortAscending("name")
                .selectAll()) {
            namedHistories.add(h);
        }

        PaginatedResult<History> historiesResult;

        if (page.getCmsTool().isUseOldHistoryIndex()) {
            historiesResult = Query
                    .from(History.class)
                    .where("name = missing and objectId = ?", state.getId())
                    .sortDescending("updateDate")
                    .select(0, 10);

        } else {
            historiesResult = Query
                    .from(History.class)
                    .where("name = missing and getObjectIdUpdateDate ^= ?", state.getId().toString())
                    .sortDescending("getObjectIdUpdateDate")
                    .select(0, 10);
        }

        for (History h : historiesResult.getItems()) {
            histories.add(h);
        }

        page.writeStart("div", "class", "widget");
            page.writeStart("h1", "class", "icon icon-object-history");
                page.writeHtml(page.localize(ContentRevisions.class, "title"));
            page.writeEnd();

            State originalState = State.getInstance(Query.fromAll()
                    .where("_id = ?", object)
                    .noCache()
                    .first());

            page.writeStart("ul", "class", "links");
                page.writeStart("li", "class", object.equals(selected) ? "selected" : null);
                    page.writeStart("a", "href", page.originalUrl(null, object));
                        page.writeHtml(ObjectUtils.firstNonNull(
                                originalState.getVisibilityLabel(),
                                page.localize(ContentRevisions.class, "action.viewLive")));
                    page.writeEnd();
                page.writeEnd();
            page.writeEnd();

            if (!scheduled.isEmpty()) {
                page.writeStart("h2");
                    page.writeHtml(page.localize(ContentRevisions.class, "subtitle.scheduled"));
                page.writeEnd();

                page.writeStart("ul", "class", "links pageThumbnails");
                    for (Draft d : scheduled) {
                        Schedule s = d.getSchedule();
                        String sn = s.getName();

                        page.writeStart("li",
                                "class", d.equals(selected) ? "selected" : null,
                                "data-preview-url", JspUtils.getAbsolutePath(page.getRequest(), "/_preview", "_cms.db.previewId", d.getId()));
                            page.writeStart("a", "href", page.objectUrl(null, d));
                                if (ObjectUtils.isBlank(sn)) {
                                    // TODO: LOCALIZE
                                    page.writeHtml(page.formatUserDateTime(s.getTriggerDate()));
                                    page.writeHtml(" by ");
                                    page.writeObjectLabel(s.getTriggerUser());

                                } else {
                                    page.writeHtml(sn);
                                }
                            page.writeEnd();
                        page.writeEnd();
                    }
                page.writeEnd();
            }

            ObjectType type = state.getType();

            if (type != null && type.as(ToolUi.class).isPublishable()) {
                page.writeStart("h2");
                    page.writeHtml("Drafts");
                page.writeEnd();

                page.writeStart("ul", "class", "links pageThumbnails");
                    page.writeStart("li", "class", "new");
                        page.writeStart("a",
                                "href", page.cmsUrl("/content/edit/new-draft", "id", state.getId()),
                                "target", "content-edit-new-draft");
                            page.writeHtml(page.localize(Draft.class, "action.newType"));
                        page.writeEnd();
                    page.writeEnd();

                    for (Draft d : drafts) {
                        String name = d.getName();
                        Content.ObjectModification dcd = d.as(Content.ObjectModification.class);

                        page.writeStart("li",
                                "class", d.equals(selected) ? "selected" : null,
                                "data-preview-url", JspUtils.getAbsolutePath(page.getRequest(), "/_preview", "_cms.db.previewId", d.getId()));
                        page.writeStart("a", "href", page.objectUrl(null, d));
                        // TODO: LOCALIZE
                                if (!ObjectUtils.isBlank(name)) {
                                    page.writeHtml(name);
                                    page.writeHtml(" - ");
                                }
                                page.writeHtml(page.formatUserDateTime(dcd.getUpdateDate()));
                                page.writeHtml(" by ");
                                page.writeObjectLabel(dcd.getUpdateUser());
                            page.writeEnd();
                        page.writeEnd();
                    }
                page.writeEnd();
            }

            if (!namedHistories.isEmpty()) {
                page.writeStart("h2");
                    page.writeHtml(page.localize(ContentRevisions.class, "subtitle.namedPast"));
                page.writeEnd();

                page.writeStart("ul", "class", "links pageThumbnails");
                    for (History h : namedHistories) {
                        page.writeStart("li",
                                "class", h.equals(selected) ? "selected" : null,
                                "data-preview-url", JspUtils.getAbsolutePath(page.getRequest(), "/_preview", "_cms.db.previewId", h.getId()));
                            page.writeStart("a", "href", page.objectUrl(null, h));
                                writeHistoryLabel(page, h);
                            page.writeEnd();
                        page.writeEnd();
                    }
                page.writeEnd();
            }

            if (!histories.isEmpty()) {
                page.writeStart("h2").writeHtml("Past").writeEnd();

                if (historiesResult.hasNext()) {
                    page.writeStart("p");
                        page.writeStart("a",
                                "class", "icon icon-action-search",
                                "target", "_top",
                                "href", page.cmsUrl("/searchAdvancedFull",
                                        Search.IGNORE_SITE_PARAMETER, "true",
                                        Search.SELECTED_TYPE_PARAMETER, ObjectType.getInstance(History.class).getId(),
                                        Search.ADVANCED_QUERY_PARAMETER, "objectId = " + state.getId()));
                            page.writeHtml(page.localize(
                                    ContentRevisions.class,
                                    ImmutableMap.of("count", historiesResult.getCount()),
                                    "action.viewAll"));
                        page.writeEnd();
                    page.writeEnd();

                    page.writeStart("h2");
                        page.writeHtml("Past 10");
                    page.writeEnd();
                }

                page.writeStart("ul", "class", "links pageThumbnails");
                    for (History h : histories) {
                        page.writeStart("li",
                                "class", h.equals(selected) ? "selected" : null,
                                "data-preview-url", JspUtils.getAbsolutePath(page.getRequest(), "/_preview", "_cms.db.previewId", h.getId()));

                            page.writeStart("a", "href", page.objectUrl(null, h));
                                writeHistoryLabel(page, h);
                            page.writeEnd();
                        page.writeEnd();
                    }
                page.writeEnd();
            }
        page.writeEnd();
    }

    private void writeHistoryLabel(ToolPageContext page, History history) throws IOException {
        Object original = history.getObject();
        String visibilityLabel = page.createVisibilityLabel(original);

        if (!ObjectUtils.isBlank(visibilityLabel)) {
            page.writeStart("span", "class", "visibilityLabel");
            page.writeHtml(visibilityLabel);
            page.writeEnd();
            page.writeHtml(" ");
        }

        page.writeObjectLabel(history);
    }

    @Override
    public void update(ToolPageContext page, Object object) {
    }
}
