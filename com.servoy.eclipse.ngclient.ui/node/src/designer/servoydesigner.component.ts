import { Component, OnInit, ViewChild, AfterViewInit, OnDestroy, ElementRef, Renderer2 } from '@angular/core';
import { WindowRefService } from '@servoy/public';
import { WebsocketSession, WebsocketService } from '../sablo/websocket.service';
import { FormService } from '../ngclient/form.service';
import { EditorContentService } from './editorcontent.service';
import { ServicesService, ServiceProvider } from '../sablo/services.service';
import { DesignFormComponent } from './designform_component.component';

import { Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

@Component({
    selector: 'servoy-designer',
    templateUrl: './servoydesigner.component.html'
})
export class ServoyDesignerComponent implements OnInit, AfterViewInit, OnDestroy, IDesignFormComponent {

    @ViewChild(DesignFormComponent) designFormComponent: DesignFormComponent;
    @ViewChild('element', { static: false }) set elementRef(elementRef: ElementRef) {
        if (elementRef) {
            this.elementRefInit = elementRef;
            this.resizeObserver.observe(this.elementRefInit.nativeElement);
        }
    }
    elementRefInit: ElementRef;

    private resizeObserver: ResizeObserver;
    mainForm: string;
    solutionName: string;
    private wsSession: WebsocketSession;

    constructor(private windowRef: WindowRefService,
        private websocketService: WebsocketService,
        private formService: FormService,
        private services: ServicesService,
        private editorContentService: EditorContentService,
        protected renderer: Renderer2,
        @Inject(DOCUMENT) private doc: Document) { }

    ngOnInit() {
        this.renderer.setStyle(this.doc.body, "overflow", "hidden");
        let path: string = this.windowRef.nativeWindow.location.pathname;
        let formStart = path.indexOf('/form/') + 6;
        let formName = path.substring(formStart, path.indexOf('/', formStart));
        this.solutionName = path.substring(path.indexOf('/solution/') + 10, path.indexOf('/form/'));
        let clientnr = path.substring(path.indexOf('/clientnr/') + 10);
        this.websocketService.setPathname('/rfb/angular/content/');
        this.wsSession = this.websocketService.connect('', [clientnr, formName, '1'], { solution: this.solutionName });
        this.wsSession.callService("$editor", "getData", {
            form: formName,
            solution: this.solutionName,
            ng2: true
        }, false).then((data: string) => {
            const formState = JSON.parse(data)[formName];
            this.formService.createFormCache(formName, formState, null);
            this.mainForm = formName;
        });
        this.wsSession.callService("$editor", "getStyleSheets", {
            form: formName,
            solution: this.solutionName,
            ng2: true
        }).then((paths: string[]) => {
            if (paths) {
                for (const path of paths) {
                    const link: HTMLLinkElement = this.doc.createElement('link');
                    link.setAttribute('rel', 'stylesheet');
                    this.doc.head.appendChild(link);
                    link.setAttribute('href', path);
                    if (path.indexOf('resources/fs/') >= 0) link.setAttribute('svy-stylesheet', path);
                }
            }
        });
        let _editorContentService = this.editorContentService;
        this.editorContentService.setDesignFormComponent(this);
        this.services.setServiceProvider({
            getService(name: string) {
                if (name == '$editorContentService') {
                    return _editorContentService;
                }
                return null;
            }
        } as ServiceProvider);
        this.resizeObserver = new ResizeObserver(() => {
            this.windowRef.nativeWindow.parent.postMessage({ id: 'contentSizeChanged' }, '*');
        });
    }

    getFormName() {
        return this.mainForm;
    }

    refresh() {
        this.designFormComponent.formCacheChanged();
    }

    renderGhosts() {
        this.windowRef.nativeWindow.parent.postMessage({ id: 'renderGhosts' }, '*');
    }

    redrawDecorators() {
        this.windowRef.nativeWindow.parent.postMessage({ id: 'redrawDecorators' }, '*');
    }

    updateForm(width: number, height: number): void {
        // not sure how to fix that sometimes wrong calls come from server; for example when deleting a component from css position container
        if (width != 0 && height != 0) {
            this.windowRef.nativeWindow.parent.postMessage({ id: 'updateFormSize', width: width, height: height }, '*');
        }
    }

    ngAfterViewInit() {
        this.windowRef.nativeWindow.parent.postMessage({ id: 'contentSizeChanged' }, '*');
    }

    ngOnDestroy() {
        if (this.resizeObserver) this.resizeObserver.unobserve(this.elementRefInit.nativeElement);
    }
}
export declare interface IDesignFormComponent {
    getFormName(): string;
    refresh(): void;
    renderGhosts(): void;
    updateForm(width: number, height: number): void;
    redrawDecorators(): void;
}