import { Input, OnChanges, SimpleChanges, Renderer2, AfterViewInit, Directive } from '@angular/core';

import {PropertyUtils, ServoyBaseComponent } from '../ngclient/servoy_public';


@Directive()
export class ServoyDefaultBaseComponent extends ServoyBaseComponent implements AfterViewInit, OnChanges {

    @Input() onActionMethodID;
    @Input() onRightClickMethodID;
    @Input() onDoubleClickMethodID;

    @Input() background;
    @Input() borderType;
    @Input() dataProviderID;
    @Input() displaysTags;
    @Input() enabled;
    @Input() fontType;
    @Input() foreground;
    @Input() format;
    @Input() horizontalAlignment;
    @Input() location;
    @Input() margin;
    @Input() size;
    @Input() styleClass;
    @Input() tabSeq;
    @Input() text;
    @Input() toolTipText;
    @Input() transparent;
    @Input() visible;
    @Input() scrollbars;

    timeoutID: number;

    constructor(protected readonly renderer: Renderer2) {
        super(renderer);
    }

    ngAfterViewInit() {
        super.ngAfterViewInit();
        super.ngOnInit();
        this.attachHandlers();
    }

    protected attachHandlers() {
      if ( this.onActionMethodID ) {
          if (this.onDoubleClickMethodID) {
              const innerThis: ServoyDefaultBaseComponent = this;
              this.renderer.listen( this.getNativeElement(), 'click', e => {
                  if (innerThis.timeoutID) {
                      window.clearTimeout(innerThis.timeoutID);
                      innerThis.timeoutID = null;
                      // double click, do nothing
                  } else {
                      innerThis.timeoutID = window.setTimeout(function() {
                          innerThis.timeoutID = null;
                          innerThis.onActionMethodID( e );
                      }, 250); }
               });
          } else {
              this.renderer.listen( this.getNativeElement(), 'click', e => this.onActionMethodID( e ));
          }
      }
      if ( this.onRightClickMethodID ) {
        this.renderer.listen( this.getNativeElement(), 'contextmenu', e => { this.onRightClickMethodID( e ); return false; });
      }
    }

    getFocusElement(): any {
        return this.getNativeElement();
    }

    public requestFocus() {
        this.getFocusElement().focus();
    }

    public getScrollX(): number {
        return this.getNativeElement().scrollLeft;
    }

    public getScrollY(): number {
        return this.getNativeElement().scrollTop;
    }

    public setScroll(x: number, y: number) {
        this.getNativeElement().scrollLeft = x;
        this.getNativeElement().scrollTop = y;
    }

    needsScrollbarInformation(): boolean {
        return true;
    }

    ngOnChanges( changes: SimpleChanges ) {
      if (changes) {
        for ( const property of Object.keys(changes) ) {
            const change = changes[property];
            switch ( property ) {
                case 'borderType':
                    PropertyUtils.setBorder( this.getNativeElement(), this.renderer , change.currentValue);
                    break;
                case 'background':
                case 'transparent':
                    this.renderer.setStyle(this.getNativeElement(), 'backgroundColor', this.transparent ? 'transparent' : change.currentValue );
                    break;
                case 'foreground':
                    this.renderer.setStyle(this.getNativeElement(), 'color', change.currentValue );
                    break;
                case 'fontType':
                    PropertyUtils.setFont( this.getNativeElement(), this.renderer , change.currentValue);
                    break;
                case 'horizontalAlignment':
                    PropertyUtils.setHorizontalAlignment(  this.getNativeChild(), this.renderer , change.currentValue);
                    break;
                case 'scrollbars':
                    if (this.needsScrollbarInformation()) PropertyUtils.setScrollbars(this.getNativeChild(), change.currentValue);
                    break;
                case 'enabled':
                    if ( change.currentValue )
                        this.renderer.removeAttribute(this.getNativeElement(),  'disabled' );
                    else
                        this.renderer.setAttribute(this.getNativeElement(),  'disabled', 'disabled' );
                    break;
                case 'margin':
                    if ( change.currentValue ) {
                        for (const  style of Object.keys(change.currentValue)) {
                            this.renderer.setStyle(this.getNativeElement(), style, change.currentValue[style] );
                        }
                    }
                    break;
                case 'styleClass':
                    if (change.previousValue)
                        this.renderer.removeClass(this.getNativeElement(), change.previousValue );
                    if ( change.currentValue)
                        this.renderer.addClass( this.getNativeElement(), change.currentValue );
                    break;
                case 'visible':
                    PropertyUtils.setVisible( this.getNativeElement(), this.renderer , change.currentValue);
                    break;
            }
        }
      }
    }
}
