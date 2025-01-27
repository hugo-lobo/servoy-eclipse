import { Injectable } from '@angular/core';
import { FormService } from '../ngclient/form.service';
import { IDesignFormComponent } from './servoydesigner.component';
import { ComponentCache, StructureCache, FormComponentCache, FormComponentProperties, FormCache } from '../ngclient/types';
import { ConverterService } from '../sablo/converter.service';
import { IComponentCache } from '@servoy/public';

@Injectable()
export class EditorContentService {
    designFormCallback: IDesignFormComponent;

    constructor(private formService: FormService, protected converterService: ConverterService) {

    }

    setDesignFormComponent(designFormCallback: IDesignFormComponent) {
        this.designFormCallback = designFormCallback;
    }

    updateFormData(updates) {
        let data = JSON.parse(updates);
        let formCache = this.formService.getFormCacheByName(this.designFormCallback.getFormName());
        let refresh = false;
        let redrawDecorators = false;
        let reorderPartComponents: boolean;
        let reorderLayoutContainers: Array<StructureCache> = new Array();
        if (data.ng2containers) {
            data.ng2containers.forEach((elem) => {
                let container = formCache.getLayoutContainer(elem.attributes['svy-id']);
                if (container) {
                    container.classes = elem.styleclass;
                    container.attributes = elem.attributes;
                    const parentUUID = data.childParentMap[container.id].uuid;
                    let newParent = container.parent;
                    if (parentUUID) {
                        newParent = formCache.getLayoutContainer(parentUUID);
                    }
                    else {
                        newParent = formCache.mainStructure
                    }
                    if (container.parent.id != newParent.id) {
                        // we moved it to another parent
                        container.parent.removeChild(container);
                        newParent.addChild(container);
                    }
                    if (reorderLayoutContainers.indexOf(newParent) < 0) {
                        // existing layout container in parent layout container , make sure is inserted in correct position
                        reorderLayoutContainers.push(newParent);
                    }
                }
                else {
                    container = new StructureCache(elem.tagname, elem.styleclass, elem.attributes, [], elem.attributes ? elem.attributes['svy-id'] : null, elem.cssPositionContainer);
                    formCache.addLayoutContainer(container);
                    const parentUUID = data.childParentMap[container.id].uuid;
                    if (parentUUID) {
                        let parent = formCache.getLayoutContainer(parentUUID);
                        if (parent) {
                            parent.addChild(container);
                            if (reorderLayoutContainers.indexOf(parent) < 0) {
                                // new layout container in parent layout container , make sure is inserted in correct position
                                reorderLayoutContainers.push(parent);
                            }
                        }
                        else {
                            let fc = formCache.getFormComponent(parentUUID);
                            if (fc) {
                                fc.addChild(container);
                                formCache.addLayoutContainer(container);
                            }
                        }
                    }
                    else {
                        // dropped directly on form
                        if (formCache.mainStructure == null) {
                            formCache.mainStructure = new StructureCache(null, null);
                        }
                        formCache.mainStructure.addChild(container);
                        if (reorderLayoutContainers.indexOf(formCache.mainStructure) < 0) {
                            // new layout container in parent form , make sure is inserted in correct position
                            reorderLayoutContainers.push(formCache.mainStructure);
                        }
                    }
                }
            });
            refresh = true;
        }
        if (data.ng2components) {
            data.ng2components.forEach((elem) => {
                let component = formCache.getComponent(elem.name);
                if (component) {
                    redrawDecorators = this.updateComponentProperties(component, elem) || redrawDecorators;
                    // existing component updated, make sure it is in correct position relative to its sibblings
                    if (component instanceof ComponentCache && component.parent) {
                        redrawDecorators = true;
                        if (reorderLayoutContainers.indexOf(component.parent) < 0) {
                            reorderLayoutContainers.push(component.parent);
                        }
                    }
                    else if (formCache.absolute) {
                        reorderPartComponents = true;
                    }
                }
                else if (formCache.getFormComponent(elem.name) == null) {
                    redrawDecorators = true;
                    if (elem.model[ConverterService.TYPES_KEY] != null) {
                        this.converterService.convertFromServerToClient(elem.model, elem.model[ConverterService.TYPES_KEY], null,
                            (property: string) => elem.model ? elem.model[property] : elem.model);
                    }
                    if (elem.type == 'servoycoreFormcomponent' || elem.type == 'servoycoreListformcomponent') {
                        const classes: Array<string> = elem.model.styleClass ? elem.model.styleClass.trim().split(' ') : new Array();
                        const layout: { [property: string]: string } = {};
                        if (!elem.responsive) {
                            // form component content is anchored layout

                            const continingFormIsResponsive = !formCache.absolute;
                            let minHeight = elem.model.minHeight !== undefined ? elem.model.minHeight : elem.model.height; // height is deprecated in favor of minHeight but they do the same thing;
                            let minWidth = elem.model.minWidth !== undefined ? elem.model.minWidth : elem.model.width; // width is deprecated in favor of minWidth but they do the same thing;;
                            let widthExplicitlySet: boolean;

                            if (!minHeight && elem.model.containedForm) minHeight = elem.model.containedForm.formHeight;
                            if (!minWidth && elem.model.containedForm) {
                                widthExplicitlySet = false;
                                minWidth = elem.model.containedForm.formWidth;
                            } else widthExplicitlySet = true;

                            if (minHeight) {
                                layout['min-height'] = minHeight + 'px';
                                if (!continingFormIsResponsive) layout['height'] = "100%"; // allow anchoring to bottom in anchored form + anchored form component
                            }
                            if (minWidth) {
                                layout['min-width'] = minWidth + 'px'; // if the form that includes this form component is responsive and this form component is anchored, allow it to grow in width to fill responsive space

                                if (continingFormIsResponsive && widthExplicitlySet) {
                                    // if container is in a responsive form, content is anchored and width model property is explicitly set
                                    // then we assume that developer wants to really set width of the form component so it can put multiple of them inside
                                    // for example a 12grid column; that means they should not simply be div / block elements; we change float as well
                                    layout['float'] = 'left';
                                }
                            }
                        }
                        const formComponentProperties: FormComponentProperties = new FormComponentProperties(classes, layout, elem.model.servoyAttributes);
                        const fcc = new FormComponentCache(elem.name, elem.model, elem.handlers, elem.responsive, elem.position, formComponentProperties, elem.model.foundset);
                        formCache.addFormComponent(fcc);
                    }
                    else {
                        const comp = new ComponentCache(elem.name, elem.type, elem.model, elem.handlers, elem.position);
                        formCache.add(comp);
                        if (!formCache.absolute) {
                            let parentUUID = data.childParentMap[elem.name] ? data.childParentMap[elem.name].uuid : undefined;
                            if (parentUUID) {
                                let parent = formCache.getLayoutContainer(parentUUID);
                                if (parent) {
                                    parent.addChild(comp);
                                    if (reorderLayoutContainers.indexOf(parent) < 0) {
                                        // new component in layout container , make sure is inserted in correct position
                                        reorderLayoutContainers.push(parent);
                                    }
                                }
                            }
                        }
                        else if (!data.formComponentsComponents || data.formComponentsComponents.indexOf(elem.name) === -1) {
                            formCache.partComponentsCache.push(comp);
                            reorderPartComponents = true;
                        }
                    }
                }
            });
            data.ng2components.forEach((elem) => {
                //FORM COMPONENTS
                let component = formCache.getFormComponent(elem.name);
                if (component) {
                    if (data.updatedFormComponentsDesignId) {
                        var fixedName = elem.name.replace(/-/g, "_");
                        if (!isNaN(fixedName[0])) {
                            fixedName = "_" + fixedName;
                        }
                        if ((data.updatedFormComponentsDesignId.indexOf(fixedName)) != -1) {
                            refresh = true;
                            const formComponent = component as FormComponentCache;
                            if (formComponent.responsive) {
                                formComponent.items.forEach((item) => {
                                    if (item['id'] !== undefined && data.childParentMap[item['id']] === undefined) {
                                        formCache.removeLayoutContainer(item['id']);
                                        this.removeChildFromParentRecursively(item, formComponent);
                                    }
                                });
                            }
                            data.formComponentsComponents.forEach((child: string) => {
                                if (child.lastIndexOf(fixedName + '$', 0) === 0) {
                                    const formComponentComponent = formCache.getComponent(child);
                                    if (formComponent.responsive) {
                                        const container = formCache.getLayoutContainer(data.childParentMap[child]);
                                        if (container) {
                                            container.addChild(formComponentComponent);
                                        }
                                    } else {
                                        formComponent.addChild(formComponentComponent);
                                    }
                                }
                            });
                        }
                    }
                    redrawDecorators = this.updateComponentProperties(component, elem) || redrawDecorators;
                    if (!component.model.containedForm && component.items && component.items.length > 0) {
                        component.items = [];
                    }
                }

                // TODO else create FC SVY-16912
            });
            refresh = true;
        }
        let toDelete = [];
        if (data.updatedFormComponentsDesignId) {
            for (var index in data.updatedFormComponentsDesignId) {
                const fcname = data.updatedFormComponentsDesignId[index].startsWith('_') ? data.updatedFormComponentsDesignId[index].substring(1) : data.updatedFormComponentsDesignId[index];
                const fc = formCache.getFormComponent(fcname.replace(/_/g, "-"));
                //delete components of the old form component
                const found = [...formCache.componentCache.keys()].filter(comp => (comp.lastIndexOf(data.updatedFormComponentsDesignId[index] + '$', 0) === 0) && (data.formComponentsComponents.indexOf(comp) == -1));
                if (found) {
                    found.forEach(comp => fc.removeChild(formCache.getComponent(comp)));
                    toDelete.push(...found);
                }
            }
        }
        if (data.deleted) {
            toDelete = toDelete.concat(data.deleted);
        }
        if (toDelete.length > 0) {
            toDelete.forEach((elem) => {
                const comp = formCache.getComponent(elem);
                if (comp) {
                    formCache.removeComponent(elem);
                    if (!formCache.absolute) {
                        this.removeChildFromParentRecursively(comp, formCache.mainStructure);
                    }
                }
                else {
                    const fc = formCache.getFormComponent(elem);
                    if (fc) {
                        formCache.removeFormComponent(elem);
                    }
                }
            });
            refresh = true;
        }
        if (data.deletedContainers) {
            data.deletedContainers.forEach((elem) => {
                const container = formCache.getLayoutContainer(elem);
                if (container) {
                    formCache.removeLayoutContainer(elem);
                    this.removeChildFromParentRecursively(container, formCache.mainStructure);
                    this.removeChildrenRecursively(container, formCache)
                }
            });
            refresh = true;
        }
        if (reorderPartComponents) {
            // make sure the order of components in absolute layout is correct, based on formindex
            this.sortChildren(formCache.partComponentsCache);
        }
        for (let container of reorderLayoutContainers) {
            // make sure the order of components in responsive layout containers is correct, based on location
            this.sortChildren(container.items);
        }
        if (data.renderGhosts) {
            this.designFormCallback.renderGhosts();
        }
        if (refresh) {
            this.designFormCallback.refresh();
        }
        if (redrawDecorators) {
            this.designFormCallback.redrawDecorators();
        }
    }

    updateComponentProperties(component: IComponentCache, elem: any): boolean {
        let redrawDecorators = false;
        component.layout = elem.position;
        const beanConversion = elem.model[ConverterService.TYPES_KEY];
        for (const property of Object.keys(elem.model)) {
            let value = elem.model[property];
            if (beanConversion && beanConversion[property]) {
                value = this.converterService.convertFromServerToClient(value, beanConversion[property], component.model[property],
                    (prop: string) => component.model ? component.model[prop] : component.model);
            }
            if (property === 'size' && (component.model[property].width !== value.width || component.model[property].height !== value.height) ||
                property === 'location' && (component.model[property].x !== value.x || component.model[property].y !== value.y)) {
                redrawDecorators = true;
            }
            component.model[property] = value;
        }
        for (const property of Object.keys(component.model)) {
            if (elem.model[property] == undefined) {
                component.model[property] = null;
            }
        }
        return redrawDecorators;
    }

    updateForm(uuid: string, parentUuid: string, width: number, height: number) {
        /*if (formData.parentUuid !== parentUuid)
        {
            this.contentRefresh();
        }*/
        this.designFormCallback.updateForm(width, height);
    }
    contentRefresh() {

    }

    updateStyleSheets() {

    }

    setDirty() {

    }

    private removeChildFromParentRecursively(child: ComponentCache | StructureCache | FormComponentCache, parent: StructureCache | FormComponentCache) {
        if (!parent.removeChild(child)) {
            if (parent.items) {
                parent.items.forEach((elem) => {
                    if (elem instanceof StructureCache) {
                        this.removeChildFromParentRecursively(child, elem);
                    }
                });
            }
        }
    }

    private removeChildrenRecursively(parent: StructureCache, formCache: FormCache) {
        if (parent.items) {
            parent.items.forEach((elem) => {
                if (elem instanceof StructureCache) {
                    formCache.removeLayoutContainer(elem.id);
                    this.removeChildrenRecursively(elem, formCache);
                }
                else if (elem instanceof ComponentCache){
                    formCache.removeComponent(elem.name);
                }
                else if (elem instanceof FormComponentCache){
                    formCache.removeFormComponent(elem.name);
                }
            });
        }
    }

    private sortChildren(items: Array<StructureCache | ComponentCache | FormComponentCache>) {
        if (items) {
            items.sort((comp1, comp2): number => {
                let priocomp1 = comp1 instanceof StructureCache ? parseInt(comp1.attributes["svy-priority"]) : parseInt(comp1.model.servoyAttributes["svy-priority"]);
                let priocomp2 = comp2 instanceof StructureCache ? parseInt(comp2.attributes["svy-priority"]) : parseInt(comp2.model.servoyAttributes["svy-priority"]);
                // priority is location in responsive form and formindex in absolute form
                if (priocomp2 > priocomp1) {
                    return -1;
                }
                return 1;
            });
        }
    }
}