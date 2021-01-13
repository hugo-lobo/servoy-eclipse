import { Injectable } from "@angular/core";

@Injectable()
export class DatagridService {
    public iconConfig;
    public toolPanelConfig;
    public gridOptions;
    public localeText;
    public columnOptions;
    public mainMenuItemsConfig;
    public arrowsUpDownMoveWhenEditing: string;

    /**
     * Creates an empty icon configuration object
     * @return {iconConfig}
     */
    createIconConfig() {
        return {
            iconGroupExpanded: "glyphicon glyphicon-minus ag-icon",
            iconGroupContracted: "glyphicon glyphicon-plus ag-icon",
            iconRefreshData: "glyphicon glyphicon-refresh"
        };
    }

    /**
     * Creates an empty toolpanel configuration object
     * @return {toolPanelConfig}
     */
    createToolPanelConfig() {
        return {};
    }

    /**
     * Creates an empty mainMenuItems configuration object
     * @return {mainMenuItemsConfig}
     */
    createMainMenuItemsConfig() {
        return {};
    }
}