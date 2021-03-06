/*
 * Copyright (c) 2016-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.page.self;

import com.evolveum.midpoint.gui.api.component.BasePanel;
import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.RoleSelectionSpecification;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.TypeFilter;
import com.evolveum.midpoint.schema.constants.RelationTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.assignment.AssignmentEditorDto;
import com.evolveum.midpoint.web.component.assignment.GridViewComponent;
import com.evolveum.midpoint.web.component.assignment.RoleCatalogItemButton;
import com.evolveum.midpoint.web.component.data.ObjectDataProvider;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.page.admin.users.dto.UserDtoStatus;
import com.evolveum.midpoint.web.session.RoleCatalogStorage;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AbstractRoleType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AssignmentType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RoleManagementConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.markup.repeater.data.IDataProvider;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by honchar.
 */
public abstract class AbstractShoppingCartTabPanel<R extends AbstractRoleType> extends BasePanel {
    private static final long serialVersionUID = 1L;

    private static final String ID_SHOPPING_CART_ITEMS_PANEL = "shoppingCartItemsPanel";

    private static final String DOT_CLASS = AbstractShoppingCartTabPanel.class.getName() + ".";
    private static final String OPERATION_LOAD_ASSIGNABLE_ROLES = DOT_CLASS + "loadAssignableRoles";
    private static final Trace LOGGER = TraceManager.getTrace(AbstractShoppingCartTabPanel.class);

    private RoleManagementConfigurationType roleManagementConfig;

    public AbstractShoppingCartTabPanel(String id, RoleManagementConfigurationType roleManagementConfig){
        super(id);
        this.roleManagementConfig = roleManagementConfig;
    }

    @Override
    protected void onInitialize(){
        super.onInitialize();
        initLayout();
    }

    private void initLayout(){
        setOutputMarkupId(true);

        initLeftSidePanel();

        GridViewComponent<AssignmentEditorDto> catalogItemsGrid = new GridViewComponent(ID_SHOPPING_CART_ITEMS_PANEL,
                new LoadableModel<IDataProvider>() {
                    @Override
                    protected IDataProvider load() {
                        return getTabPanelProvider();
                    }
                }) {
            private static final long serialVersionUID = 1L;

//            @Override
//            protected void onBeforeRender(){
//                super.onBeforeRender();
//                add(getCatalogItemsPanelClassAppender());
//            }

            @Override
            protected void populateItem(Item item) {
                item.add(new RoleCatalogItemButton(getCellItemId(), item.getModel()){
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void assignmentAddedToShoppingCartPerformed(AjaxRequestTarget target){
                        AbstractShoppingCartTabPanel.this.assignmentAddedToShoppingCartPerformed(target);
                    }
                });
            }
        };
        catalogItemsGrid.add(new VisibleEnableBehaviour(){
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isVisible(){
                return isShoppingCartItemsPanelVisible();
            }
        });
        appendItemsPanelStyle(catalogItemsGrid);
        catalogItemsGrid.setOutputMarkupId(true);
        add(catalogItemsGrid);
    }

    protected void initLeftSidePanel(){
    }

    private ObjectDataProvider getTabPanelProvider() {
        ObjectDataProvider provider = new ObjectDataProvider<AssignmentEditorDto, AbstractRoleType>(AbstractShoppingCartTabPanel.this,
                AbstractRoleType.class) {
            private static final long serialVersionUID = 1L;

            @Override
            public AssignmentEditorDto createDataObjectWrapper(PrismObject<AbstractRoleType> obj) {

                AssignmentEditorDto dto = AssignmentEditorDto.createDtoFromObject(obj.asObjectable(), UserDtoStatus.ADD, getPageBase());
                if (!getRoleCatalogStorage().isMultiUserRequest()) {
                    dto.setAlreadyAssigned(isAlreadyAssigned(obj, dto));
                    dto.setDefualtAssignmentConstraints(roleManagementConfig == null ? null : roleManagementConfig.getDefaultAssignmentConstraints());
                }
                return dto;
            }

            @Override
            public ObjectQuery getQuery() {
                return createContentQuery(null);
            }
        };
        return provider;
    }

    private boolean isAlreadyAssigned(PrismObject<AbstractRoleType> obj, AssignmentEditorDto assignmentDto){
        UserType user = getTargetUser();
        if (user == null || user.getAssignment() == null){
            return false;
        }
        boolean isAssigned = false;
        List<RelationTypes> assignedRelationsList = new ArrayList<>();
        for (AssignmentType assignment : user.getAssignment()){
            if (assignment.getTargetRef() != null && assignment.getTargetRef().getOid().equals(obj.getOid())){
                isAssigned = true;
                assignedRelationsList.add(RelationTypes.getRelationType(assignment.getTargetRef().getRelation()));
            }
        }
        assignmentDto.setAssignedRelationsList(assignedRelationsList);
        return isAssigned;
    }

    protected boolean isShoppingCartItemsPanelVisible(){
        return true;
    }

    protected void appendItemsPanelStyle(GridViewComponent itemsPanel){
        itemsPanel.add(AttributeAppender.append("class", "col-md-12"));
    }

    protected ObjectQuery createContentQuery(ObjectQuery searchQuery) {
        ObjectQuery memberQuery = new ObjectQuery();
        memberQuery.addFilter(getAssignableRolesFilter());
        if (getQueryType() != null){
            ObjectFilter typeFilter = ObjectQueryUtil.filterAnd(TypeFilter.createType(getQueryType(), null), memberQuery.getFilter());
            memberQuery.addFilter(typeFilter);
        }

        if (memberQuery == null) {
            memberQuery = new ObjectQuery();
        }
//        if (searchQuery == null) {
//            if (searchModel != null && searchModel.getObject() != null) {
//                Search search = searchModel.getObject();
//                searchQuery = search.createObjectQuery(getPrismContext());
//            }
//        }
        if (searchQuery != null && searchQuery.getFilter() != null) {
            memberQuery.addFilter(searchQuery.getFilter());
        }
        return memberQuery;
    }

    private ObjectFilter getAssignableRolesFilter() {
        ObjectFilter filter = null;
        LOGGER.debug("Loading roles which the current user has right to assign");
        Task task = getPageBase().createSimpleTask(OPERATION_LOAD_ASSIGNABLE_ROLES);
        OperationResult result = task.getResult();
        try {
            ModelInteractionService mis = getPageBase().getModelInteractionService();
            RoleSelectionSpecification roleSpec =
                    mis.getAssignableRoleSpecification(getTargetUser().asPrismObject(), task, result);
            filter = roleSpec.getFilter();
        } catch (Exception ex) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't load available roles", ex);
            result.recordFatalError("Couldn't load available roles", ex);
        } finally {
            result.recomputeStatus();
        }
        if (!result.isSuccess() && !result.isHandledError()) {
            getPageBase().showResult(result);
        }
        return filter;
    }

    protected abstract QName getQueryType();

    private UserType getTargetUser(){
        if (getRoleCatalogStorage().isSelfRequest()){
            return getPageBase().loadUserSelf().asObjectable();
        }
        return getRoleCatalogStorage().getTargetUserList().get(0);
    }

    protected void assignmentAddedToShoppingCartPerformed(AjaxRequestTarget target){
    }

    protected RoleCatalogStorage getRoleCatalogStorage(){
        return getPageBase().getSessionStorage().getRoleCatalog();
    }

    protected GridViewComponent getGridViewComponent(){
        return (GridViewComponent)get(ID_SHOPPING_CART_ITEMS_PANEL);
    }
}
