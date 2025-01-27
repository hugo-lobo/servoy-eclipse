import { DOCUMENT } from '@angular/common';
import { Component, Inject, OnInit, Renderer2 } from '@angular/core';
import { DragItem } from '../palette/palette.component';
import { DesignerUtilsService } from '../services/designerutils.service';
import { EditorSessionService, ISupportAutoscroll } from '../services/editorsession.service';

@Component({
  selector: 'dragselection-responsive',
  templateUrl: './dragselection-responsive.component.html'
})
export class DragselectionResponsiveComponent implements OnInit, ISupportAutoscroll {
  allowedChildren: any;
  dragNode: HTMLElement;
  urlParser: any;
  dragStartEvent: MouseEvent;
  initialParent: any;
  dragging: boolean;
  glasspane: HTMLElement;
  highlightEl: HTMLElement;
  dropHighlight: string;
  autoscrollElementClientBounds: Array<DOMRect>;
  autoscrollAreasEnabled: any;
  canDrop: { dropAllowed: boolean; dropTarget?: Element; beforeChild?: Element; append?: boolean; };
  dragItem: DragItem = {};
  dragCopy: boolean;
  
  constructor(protected readonly editorSession: EditorSessionService, @Inject(DOCUMENT) private doc: Document, protected readonly renderer: Renderer2, private readonly designerUtilsService: DesignerUtilsService) { }

  ngOnInit(): void {
    this.glasspane = this.doc.querySelector('.contentframe-overlay') as HTMLElement;
    const content = this.doc.querySelector('.content-area') as HTMLElement;
    content.addEventListener('mousedown', (event) => this.onMouseDown(event));
    content.addEventListener('mouseup', (event) => this.onMouseUp(event));
    content.addEventListener('mousemove', (event) => this.onMouseMove(event));
    content.addEventListener('keyup', (event) => this.onKeyup(event));
}

  onKeyup(event: KeyboardEvent) {
    //if control is released during drag, the copy is deleted and the original element must be moved
    if (this.dragCopy && this.dragStartEvent && this.dragStartEvent.ctrlKey && (event.code.startsWith('Control') || event.code.startsWith('Meta'))) {
      this.doc.querySelector('iframe').contentWindow.postMessage({ id: 'removeDragCopy', uuid: this.dragNode.getAttribute("svy-id"),
        insertBefore: this.canDrop.beforeChild ? this.canDrop.beforeChild.getAttribute('svy-id') : null}, '*');
      this.dragCopy = false;
    }
  }

  onMouseDown(event: MouseEvent) {
    if (this.editorSession.getState().dragging || event.buttons !== 1) return; //prevent dnd when dragging from palette
    if (this.editorSession.getSelection() != null && this.editorSession.getSelection().length > 1)
    {
        // do not allow drag of multiple elements in responsive design
        return;
    }
    this.dragNode = this.designerUtilsService.getNode(this.doc, event) as HTMLElement;
    if (!this.dragNode) return; 

    // do not allow moving elements inside css position container in responsive layout
    if (this.dragNode && this.findAncestor(this.dragNode, '.svy-csspositioncontainer') !== null)
        return;

    // skip dragging if it is an child element of a form reference
    if (event.button == 0 && this.dragNode) {
      this.dragStartEvent = event;
      this.initialParent = null;

      if (this.dragNode.classList.contains("formComponentChild")) {//do not grab if this is a form component element
        this.dragStartEvent = null;
      }
      this.initialParent = this.designerUtilsService.getParent(this.dragNode, this.dragNode.getAttribute("svy-layoutname") ? "layout" : "component");

      this.highlightEl = this.dragNode.cloneNode(true) as HTMLElement;
      if (this.dragNode.clientWidth == 0 && this.dragNode.clientHeight == 0) {
        if (this.dragNode.firstElementChild) {
          this.highlightEl = this.dragNode.firstElementChild.cloneNode(true) as HTMLElement;
        }
        else if (!this.dragNode.getAttribute("svy-layoutname")){
          this.highlightEl = this.dragNode.parentElement.cloneNode(true) as HTMLElement; //component
        }
      }

      this.renderer.addClass(this.highlightEl, 'highlight_element');
      this.renderer.removeAttribute(this.highlightEl, 'svy-id');

      this.dragItem.topContainer = this.designerUtilsService.isTopContainer(this.dragNode.getAttribute("svy-layoutname"));
      this.dragItem.layoutName = this.dragNode.getAttribute("svy-layoutname");
      this.dragItem.componentType = this.dragItem.layoutName ? "layout" : "component";
    }
  }

  onMouseMove(event: MouseEvent) {
    if (!this.dragStartEvent || event.buttons !== 1 || !this.dragNode) return;
    if (!this.editorSession.getState().dragging) {
      if (Math.abs(this.dragStartEvent.clientX - event.clientX) > 5 || Math.abs(this.dragStartEvent.clientY - event.clientY) > 5) {
        this.editorSession.getState().dragging = true;
        this.dragCopy = event.ctrlKey || event.metaKey;
        this.doc.querySelector('iframe').contentWindow.postMessage({ id: 'createDraggedComponent', uuid: this.dragNode.getAttribute("svy-id"), dragCopy: this.dragCopy }, '*');
        this.autoscrollElementClientBounds = this.designerUtilsService.getAutoscrollElementClientBounds(this.doc);
          if (this.dropHighlight !== this.dragItem.layoutName) {
            const elements = this.dragNode.querySelectorAll('[svy-id]');
            const dropHighlightIgnoredIds = Array.from(elements).map((element) => { 
                return element.getAttribute('svy-id');
            });
            this.editorSession.sendState('dropHighlight', { dropHighlight : this.dragItem.layoutName, dropHighlightIgnoredIds : dropHighlightIgnoredIds});
            this.dropHighlight = this.dragItem.layoutName;
          }
        this.editorSession.getState().drop_highlight = this.dragItem.componentType;
      } else return;
    }

    if (this.autoscrollElementClientBounds && !this.autoscrollAreasEnabled && !this.designerUtilsService.isInsideAutoscrollElementClientBounds(this.autoscrollElementClientBounds, event.clientX, event.clientY)) {
      this.autoscrollAreasEnabled = true;
      this.editorSession.startAutoscroll(this);
    }

    if (!this.dragItem.contentItemBeingDragged) {
      const frameElem = this.doc.querySelector('iframe');
      this.dragItem.contentItemBeingDragged = frameElem.contentWindow.document.getElementById('svy_draggedelement');
    }
    if (this.dragItem.contentItemBeingDragged){
      const point = this.adjustPoint(event.pageX + 1, event.pageY + 1);
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'top',  point.y + 'px');
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'left',  point.x + 'px');
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'position', 'absolute');
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'display', 'block' );
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'z-index', 4 );
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'transition', 'opacity .5s ease-in-out 0' );
    }

    this.canDrop = this.getDropNode(this.dragItem.componentType, this.dragItem.topContainer, this.dragNode.getAttribute("svy-layoutname"), event, this.dragNode.getAttribute("svy-id"));
    if (!this.canDrop.dropAllowed) {
      this.glasspane.style.cursor = "not-allowed";
    } else this.glasspane.style.cursor = "pointer";

    this.dragStartEvent = event;

    if (this.canDrop.beforeChild && this.canDrop.beforeChild.getAttribute("svy-id") === this.dragNode.getAttribute("svy-id")) {
      this.canDrop.beforeChild = this.canDrop.beforeChild.nextElementSibling;
    }

    if (this.glasspane.style.cursor === "pointer") {
          if (this.canDrop.dropAllowed && this.canDrop.dropTarget === this.dragNode.parentNode && this.canDrop.beforeChild === this.dragNode.nextElementSibling) {
            this.canDrop.dropAllowed = false; //it does not make sense to drop exactly where it is
          }
          if (this.canDrop.dropAllowed) {
              this.renderer.setStyle(this.dragNode, 'opacity', '1');
              const frameElem = this.doc.querySelector('iframe');
              frameElem.contentWindow.postMessage({
                  id: 'insertDraggedComponent',
                  dropTarget: this.canDrop.dropTarget ? this.canDrop.dropTarget.getAttribute('svy-id') : null,
                  insertBefore: this.canDrop.beforeChild ? this.canDrop.beforeChild.getAttribute('svy-id') : null
              }, '*');
          }
    }
    else if (this.dragItem.contentItemBeingDragged) {
      this.renderer.setStyle(this.dragItem.contentItemBeingDragged, 'opacity', '1');
    }
  }
  
  onMouseUp(event: MouseEvent) {
    if (this.dragStartEvent !== null && this.dragNode && this.editorSession.getState().dragging && this.canDrop.dropAllowed) {

      let obj = (event.ctrlKey || event.metaKey) ? [] : {};
     
      if (!this.canDrop.beforeChild && !this.canDrop.append) {
        this.canDrop.beforeChild = this.dragNode.nextElementSibling;
      }

      if (this.canDrop.beforeChild && this.canDrop.beforeChild.getAttribute("svy-id") === this.dragNode.getAttribute("svy-id")) {
        this.canDrop.beforeChild = this.canDrop.beforeChild.nextElementSibling;
      }

      let key = (event.ctrlKey || event.metaKey) && this.dragCopy ? 0 : this.dragNode.getAttribute("svy-id");
      obj[key] = {};
      if ((event.ctrlKey || event.metaKey) && this.dragCopy) {
        obj[key].uuid = this.dragNode.getAttribute('svy-id');
      }

      if (this.canDrop.dropTarget) {
        obj[key].dropTargetUUID = this.canDrop.dropTarget.getAttribute("svy-id");
      }

      if (this.canDrop.beforeChild) {
        obj[key].rightSibling = this.canDrop.beforeChild.getAttribute("svy-id");
      }
      if (event.ctrlKey || event.metaKey) {
        this.editorSession.createComponents({
          "components": obj
        });
      } else {
        this.editorSession.getSession().callService('formeditor', 'moveComponent', obj, true);
      }
    }
    
    //disable mouse events on the autoscroll
    this.editorSession.getState().pointerEvents = 'none'; 
    this.autoscrollAreasEnabled = false;
    this.editorSession.stopAutoscroll();
    
    this.dragStartEvent = null;
    this.editorSession.getState().dragging = false;
    this.glasspane.style.cursor = "default";
    this.dragNode = null;
    this.dropHighlight = null;
    if (this.dragItem && this.dragItem.contentItemBeingDragged) {
      const frameElem = this.doc.querySelector('iframe');
      frameElem.contentWindow.postMessage({ id: 'destroyElement', existingElement: !this.dragCopy }, '*');
    }
    //force redrawing of the selection decorator to the new position
    //this.editorSession.updateSelection(this.editorSession.getSelection());
     this.editorSession.sendState('dropHighlight', null);
    this.dragItem = {};
    this.dragCopy = false;
  }

  private findAncestor(el: HTMLElement, cls: string): HTMLElement {
    while ((el = el.parentElement) && !el.classList.contains(cls));
    return el !== undefined && el !== null && el.classList.contains(cls) ? el : null;
  }

  private adjustPoint(x: number, y: number): {x: number, y: number} {
    const style = window.getComputedStyle( this.glasspane.parentElement);
    const rectangle = this.glasspane.getBoundingClientRect();
    return {x: x - parseInt(style.paddingLeft) - rectangle.left, y: y - parseInt(style.paddingLeft) - rectangle.top};
  }

  private getDropNode(type: string, topContainer: boolean, layoutName: string, event: MouseEvent, svyId: string)
  {
    const canDrop = this.designerUtilsService.getDropNode(this.doc, type, topContainer, layoutName, event, undefined, svyId);
    canDrop.dropAllowed = canDrop.dropAllowed && this.dragNode.classList.contains("inheritedElement")
        && this.initialParent !== null && this.initialParent[0].getAttribute("svy-id") !== canDrop.dropTarget.getAttribute("svy-id") ? false : canDrop.dropAllowed;
    return canDrop;
  }

  getUpdateLocationCallback(): (changeX: number, changeY: number, minX?: number, minY?: number) => void{
    return null;
  }
}