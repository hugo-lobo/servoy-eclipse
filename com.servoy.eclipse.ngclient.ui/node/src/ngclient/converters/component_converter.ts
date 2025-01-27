import { IConverter, PropertyContext, ConverterService } from '../../sablo/converter.service';
import { LoggerService, LoggerFactory, ViewportRowUpdates, IChangeAwareValue, instanceOfChangeAwareValue, FoundsetChangeEvent } from '@servoy/public';
import { FoundsetViewportState, ViewportService } from '../services/viewport.service';
import { FoundsetConverter } from './foundset_converter';
import { FoundsetLinkedConverter } from './foundsetLinked_converter';
import { ComponentCache } from '../types';
import { SabloService } from '../../sablo/sablo.service';

export class ComponentConverter implements IConverter {

    static readonly PROPERTY_UPDATES_KEY = 'propertyUpdates';
    static readonly MODEL_KEY = 'model';
    static readonly MODEL_VIEWPORT_KEY = 'model_vp';
    static readonly MODEL_VIEWPORT_CHANGES_KEY = 'model_vp_ch';
    static readonly MODEL_VIEWPORT = 'modelViewport';
    static readonly PROPERTY_NAME_KEY = 'pn';
    static readonly VALUE_KEY = 'v';
    static readonly NO_OP = 'n';
    static readonly RECORD_BASED_PROPERTIES = 'recordBasedProperties';

    private log: LoggerService;

    constructor(private converterService: ConverterService, private viewportService: ViewportService, 
        private sabloService: SabloService,logFactory: LoggerFactory) {
        this.log = logFactory.getLogger('ComponentConverter');
    }

    fromServerToClient(serverSentData: any, currentClientData: ComponentModel, propertyContext: PropertyContext): ComponentModel {
        let newValue = currentClientData;

        // see if someone is listening for changes on current value; if so, prepare to fire changes at the end of this method
        const hasListeners = (currentClientData && currentClientData.getStateHolder().viewportChangeListeners.length > 0);
        const notificationParamForListeners: FoundsetChangeEvent = hasListeners ? {} : undefined;

        if (serverSentData && serverSentData[ComponentConverter.PROPERTY_UPDATES_KEY]) {
            // granular updates received
            const componentModel = currentClientData;
            const beanUpdate = serverSentData[ComponentConverter.PROPERTY_UPDATES_KEY];

            const modelBeanUpdate = beanUpdate[ComponentConverter.MODEL_KEY];
            const wholeViewportUpdate = beanUpdate[ComponentConverter.MODEL_VIEWPORT_KEY];
            const viewportUpdate = beanUpdate[ComponentConverter.MODEL_VIEWPORT_CHANGES_KEY];
            let done = false;

            if (modelBeanUpdate) {
                const beanModel = componentModel.model;

                // just dummy stuff - currently the parent controls layout, but applyBeanData needs such data...
                const beanLayout = componentModel.layout;

                const modelUpdateConversionInfo = modelBeanUpdate[ConverterService.TYPES_KEY] ? this.converterService.getOrCreateInDepthProperty(componentModel, ConverterService.TYPES_KEY)
                    : this.converterService.getInDepthProperty(componentModel, ConverterService.TYPES_KEY);

                this.applyBeanData(beanModel, beanLayout, modelBeanUpdate, componentModel,
                    modelUpdateConversionInfo, modelBeanUpdate[ConverterService.TYPES_KEY], propertyContext);
                done = true;
            }

            // if component is linked to a foundset, then record - dependent property values are sent over as as viewport representing values for the foundset property's viewport
            if (wholeViewportUpdate) {
                const oldRows = componentModel.modelViewport;
                if (oldRows === undefined) componentModel[ComponentConverter.MODEL_VIEWPORT] = [];

                componentModel.modelViewport = this.viewportService.updateWholeViewport(componentModel.modelViewport,
                    componentModel.getStateHolder(), wholeViewportUpdate, beanUpdate[ConverterService.TYPES_KEY] && beanUpdate[ConverterService.TYPES_KEY][ComponentConverter.MODEL_VIEWPORT_KEY] ?
                    beanUpdate[ConverterService.TYPES_KEY][ComponentConverter.MODEL_VIEWPORT_KEY] : undefined, propertyContext, false);
                if (hasListeners) notificationParamForListeners.viewportRowsCompletelyChanged = { oldValue: oldRows, newValue: currentClientData[ComponentConverter.MODEL_VIEWPORT] };
                done = true;
            } else if (viewportUpdate) {
                this.viewportService.updateViewportGranularly(componentModel.modelViewport, componentModel.getStateHolder(), viewportUpdate,
                    beanUpdate[ConverterService.TYPES_KEY] && beanUpdate[ConverterService.TYPES_KEY][ComponentConverter.MODEL_VIEWPORT_CHANGES_KEY] ?
                        beanUpdate[ConverterService.TYPES_KEY][ComponentConverter.MODEL_VIEWPORT_CHANGES_KEY] : undefined, propertyContext, false);
                if (hasListeners) {
                    const upd: ViewportRowUpdates = viewportUpdate[FoundsetConverter.UPDATE_PREFIX + FoundsetConverter.ROWS];
                    notificationParamForListeners.viewportRowsUpdated = upd; // viewPortUpdate[UPDATE_PREFIX + ROWS] was alre
                }

                done = true;
            }

            if (!done) {
                this.log.error('Can\'t interpret component server update correctly: ' + JSON.stringify(serverSentData, undefined, 2));
            }
        } else if (serverSentData === undefined || !serverSentData[ComponentConverter.NO_OP]) {
            if (serverSentData) {
                // full contents received
                const componentModel = new ComponentModel(
                    serverSentData.name,
                    serverSentData.componentDirectiveName,
                    serverSentData.model,
                    serverSentData.handlers,
                    serverSentData.position,
                    serverSentData.forFoundset,
                    serverSentData.foundsetConfig,
                    serverSentData.model_vp);
                newValue = componentModel;

                if (serverSentData[FoundsetLinkedConverter.FOR_FOUNDSET_PROPERTY] !== undefined) {
                    // if it's linked to a foundset, keep that info in internal state; viewport.js needs it
                    const forFoundsetPropertyName = serverSentData[FoundsetLinkedConverter.FOR_FOUNDSET_PROPERTY];
                    componentModel.getStateHolder().forFoundset = () => propertyContext(forFoundsetPropertyName);
                    delete serverSentData[FoundsetLinkedConverter.FOR_FOUNDSET_PROPERTY];
                }

                // private impl
                componentModel.getStateHolder().requests = [];

                // even if it's a completely new value, keep listeners from old one if there is an old value
                componentModel.getStateHolder().viewportChangeListeners = (currentClientData && currentClientData.getStateHolder() ? currentClientData.getStateHolder().viewportChangeListeners : []);

                /**
                 * Adds a change listener that will get triggered when server sends granular or full modelViewport changes for this component.
                 *
                 * @see $webSocket.addIncomingMessageHandlingDoneTask if you need your code to execute after all properties that were linked to this same foundset
                 * get their changes applied you can use $webSocket.addIncomingMessageHandlingDoneTask.
                 * @param listener the listener to register.
                 */
                //                newValue.addViewportChangeListener = function(listener) {
                //                    internalState.viewportChangeListeners.push(listener);
                //                    return  function() {
                //                     return newValue.removeViewportChangeListener(listener);
                //                    };
                //                };

                //                newValue.removeViewportChangeListener = function(listener) {
                //                    const index = internalState.viewportChangeListeners.indexOf(listener);
                //                    if (index > -1) {
                //                        internalState.viewportChangeListeners.splice(index, 1);
                //                    }
                //                };
                // calling applyBeanData initially to make sure any needed conversions are done on model's properties
                const beanData = serverSentData[ComponentConverter.MODEL_KEY];
                const beanModel: any = {};
                serverSentData[ComponentConverter.MODEL_KEY] = beanModel;

                const currentConversionInfo = beanData[ConverterService.TYPES_KEY] ?
                    this.converterService.getOrCreateInDepthProperty(componentModel, ConverterService.TYPES_KEY) :
                    this.converterService.getInDepthProperty(componentModel, ConverterService.TYPES_KEY);

                this.applyBeanData(beanModel, componentModel.layout, beanData, componentModel,
                    currentConversionInfo, beanData[ConverterService.TYPES_KEY], propertyContext);

                // component property is now be able to send itself entirely at runtime; we need to handle viewport conversions here as well
                const wholeViewport = serverSentData[ComponentConverter.MODEL_VIEWPORT_KEY];
                delete serverSentData[ComponentConverter.MODEL_VIEWPORT_KEY];
                serverSentData[ComponentConverter.MODEL_VIEWPORT] = [];

                if (wholeViewport) {
                    serverSentData[ComponentConverter.MODEL_VIEWPORT] = this.viewportService.updateWholeViewport(serverSentData[ComponentConverter.MODEL_VIEWPORT],
                        componentModel.getStateHolder(), wholeViewport, serverSentData[ConverterService.TYPES_KEY] ?
                        serverSentData[ConverterService.TYPES_KEY][ComponentConverter.MODEL_VIEWPORT_KEY] : undefined, propertyContext, false);
                }
                if (serverSentData[ConverterService.TYPES_KEY] !== undefined) delete serverSentData[ConverterService.TYPES_KEY];

                if (!serverSentData.api) serverSentData.api = {};
                if (serverSentData.handlers) {
                    for (const key of Object.keys(serverSentData.handlers)) {
                        componentModel.mappedHandlers.set(key, this.generateWrapperHandler(key, componentModel));
                    }
                }
            }

        }

        if (notificationParamForListeners && Object.keys(notificationParamForListeners).length > 0) {
            // if (this.log.debugEnabled && this.log.debugLevel === this.log.SPAM)
            // this.log.debug("svy component * firing founset listener notifications: " + JSON.stringify(Object.keys(notificationParamForListeners)));
            // use previous (current) value as newValue might be undefined/null and the listeners would be the same anyway
            currentClientData.getStateHolder().fireChanges(notificationParamForListeners);
        }

        return newValue;
    }

    fromClientToServer(newClientData: ComponentModel, oldClientData: ComponentModel): any {
        let retValue = [];
        if (newClientData) {
            const internalState = newClientData.getStateHolder();
            if (internalState.hasChanges()) {
                retValue = internalState.requests;
                internalState.requests = [];
            }
            let added = false;
            const propChange = { propertyChanges: {} };
            const conversionInfo = newClientData.model.svy_types;
            internalState.changes.forEach((change) => {
                let value = change.value;
                if (conversionInfo && conversionInfo[change.dp])
                    value = this.converterService.convertFromClientToServer(value, conversionInfo[change.dp], change.oldValue);
                else value = this.converterService.convertClientObject(value);
                if (change.dataprovider) {
                    const apply = { svyApply: {} };
                    if (change.rowId) apply.svyApply[ViewportService.ROW_ID_COL_KEY] = change.rowId;
                    apply.svyApply[ComponentConverter.PROPERTY_NAME_KEY] = change.dp;
                    apply.svyApply[ComponentConverter.VALUE_KEY] = value;;

                    retValue.push(apply);
                } else {
                    propChange.propertyChanges[change.dp] = value;
                    if (!added) {
                        retValue.push(propChange);
                        added = true;
                    }
                }
            });
            internalState.changes = [];
        }
        return retValue;
    }

    private generateWrapperHandler(handler: string, state: ComponentModel) {
        const executeHandler = (type, args, row, name, model) => {
            // TODO implement $uiBlocker
            // if ($uiBlocker.shouldBlockDuplicateEvents(name, model, type, row))
            // {
            //     // reject execution
            //     console.log("rejecting execution of: "+type +" on "+name +" row "+row);
            //     return $q.resolve(null);
            // }
            const deferred = this.sabloService.createDeferredWSEvent();
            const newargs = this.converterService.getEventArgs(args, type);
            state.getStateHolder().requests.push({
                handlerExec: {
                    eventType: type,
                    args: newargs,
                    rowId: row,
                    defid: deferred.cmsgid
                }
            });
            state.getStateHolder().notifyChangeListener();
            // defered.promise.finally(function(){$uiBlocker.eventExecuted(name, model, type, row);});
            return deferred.deferred.promise;
        };
        const eventHandler = (args: any, rowId: any) => executeHandler(handler, args, rowId, state.name, state.model);
        // eslint-disable-next-line prefer-arrow/prefer-arrow-functions
        const wrapper = function() {
            return eventHandler(arguments, null);
        };
        wrapper.selectRecordHandler = (rowId: any) =>
            // eslint-disable-next-line prefer-arrow/prefer-arrow-functions
            function() {
                return eventHandler(arguments, rowId instanceof Function ? rowId() : rowId);
            };
        return wrapper;
    }

    private applyBeanData(beanModel: any, beanLayout: any, beanData: any, component: ComponentModel, beanConversionInfo: any, newConversionInfo: any, propertyContext: PropertyContext) {
        if (newConversionInfo) { // then means beanConversionInfo should also be defined - we assume that
            // beanConversionInfo will be granularly updated in the loop below
            // (to not drop other property conversion info when only one property is being applied granularly to the bean)
            beanData = this.converterService.convertFromServerToClient(beanData, newConversionInfo, beanModel, propertyContext);
        }

        // apply the new values and conversion info
        for (const key of Object.keys(beanData)) {
            const oldModelValueForKey = beanModel[key];
            if (oldModelValueForKey === beanModel[key] && newConversionInfo && newConversionInfo[key] && component.nestedPropertyChange) {
                // this was a nested updated
                component.nestedPropertyChange(key, oldModelValueForKey);
            }
            beanModel[key] = beanData[key];

            // remember conversion info for when it will be sent back to server - it might need special conversion as well
            if (newConversionInfo && newConversionInfo[key]) {
                const oldConversionInfoForKey = beanConversionInfo[key];
                beanConversionInfo[key] = newConversionInfo[key];

                // if the value changed and it wants to be in control of it's changes, or if the conversion info for this value changed
                // (thus possibly preparing an old value for being change-aware without changing the value reference)
                if ((oldModelValueForKey !== beanData[key] || oldConversionInfoForKey !== newConversionInfo[key])
                    && beanData[key] && instanceOfChangeAwareValue(beanData[key])) {
                    // setChangeNotifier can be called now after the new conversion info and value are set (changeNotifierGenerator(key) will probably use the values in model
                    //  and that has to point to the new value if reference was changed)
                    // as setChangeNotifier on smart property types might end up calling the change notifier right away to announce it already has changes (because for example
                    // the convertFromServerToClient on that property type above might have triggered some listener to the component that uses it which then requested
                    // another thing from the property type and it then already has changes...) // TODO should we decouple this scenario?  if we are still processing server to client changes
                    // when change notifier is called we could trigger the change notifier later/async for sending changes back to server...
                    beanData[key].getStateHolder().setChangeListener(this.getSmartBeanPropertyChangeListenerForNonFSLinkedProps(component, key));
                    // we check for changes anyway in case a property type doesn't do it itself as described in the comment above
                    if (beanData[key].getStateHolder().hasChanges()) component.getStateHolder().notifyChangeListener();
                }
            } else if (beanConversionInfo && beanConversionInfo[key] !== undefined) delete beanConversionInfo[key]; // this prop. no longer has conversion info!
        }
    }

    // TODO the following code should be refactored to reuse what a normal form does for it's components, similar to how it was done in NG1
    // + TODO make child components (so this property type) react also to shallow/deep (equivalent to emit on a component's output) on 'dumb' properties that have no change listeners
    private getSmartBeanPropertyChangeListenerForNonFSLinkedProps(componentValue: ComponentModel, propertyName: string) {
        return () => {
            const internalState = componentValue.getStateHolder();
            const propValue = componentValue.model[propertyName];
            internalState.requests.push({ propertyChanges : this.getChildPropertyChangesForNonFSLinkedProps(componentValue, propValue, propertyName) });
            componentValue.getStateHolder().notifyChangeListener();
        }
    }

    private getChildPropertyChangesForNonFSLinkedProps(componentValue: ComponentModel, propValue: IChangeAwareValue, propertyName: string) {
        // all this should be refactored
        const changes = {};
        const conversionInfo = componentValue[ConverterService.TYPES_KEY];

        if (conversionInfo && conversionInfo[propertyName]) {
            changes[propertyName] = this.converterService.convertFromClientToServer(propValue, conversionInfo[propertyName], propValue);
        } else {
            changes[propertyName] = this.converterService.convertClientObject(propValue); // not going to happen for smart values anyway (they got 'smart' due to conversions that exist for this prop)
        }
        return changes;
    }
    // END TODO the previous code should be refactored to reuse what a normal form does for it's components, similar to how it was done in NG1

}

export class ComponentModel extends ComponentCache implements IChangeAwareValue {

    public nestedPropertyChange: (property: string, value: any) => void;
    public readonly mappedHandlers = new Map<string, { (): Promise<any>; selectRecordHandler(rowId: any): () => Promise<any> }>();
    private readonly stateHolder = new ComponentState();

    constructor(
        name: string,
        type: string,
        model: { [property: string]: any },
        handlers: Array<string>,
        layout: { [property: string]: string },
        public readonly forFoundset: string,
        public readonly foundsetConfig: any,
        public modelViewport: any[]) {
        super(name, type, model, handlers, layout);
    }

    getStateHolder(): ComponentState {
        return this.stateHolder;
    }

    startEdit(property: string, rowId: any) {
        const req = { svyStartEdit: {} };
        if (rowId) req.svyStartEdit[ViewportService.ROW_ID_COL_KEY] = rowId;
        req.svyStartEdit[ComponentConverter.PROPERTY_NAME_KEY] = property;
        this.stateHolder.requests.push(req);
        this.stateHolder.notifyChangeListener();
    }

    sendChanges(dp: string, value: any, oldValue: any, rowId: any, dataprovider?: boolean) {
        this.stateHolder.changes.push({ dp, value, oldValue, dataprovider, rowId });
        this.stateHolder.notifyChangeListener();
    }
}

class ComponentState extends FoundsetViewportState {
    changes: Array<{ dp: string; value: any; oldValue: any; dataprovider: boolean; rowId?: any }> = [];
    viewportChangeListeners: Array<(any) => void>;
    fireChanges(viewportChanges: any) {
        this.viewportChangeListeners.forEach(listener => listener(viewportChanges));
    };

}

