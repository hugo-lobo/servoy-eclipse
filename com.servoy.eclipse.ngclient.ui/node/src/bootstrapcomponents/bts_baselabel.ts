import { ServoyBootstrapBaseComponent } from "./bts_basecomp";
import { Input, ViewChild, ElementRef, Renderer2, Directive } from "@angular/core";

@Directive()
export class ServoyBootstrapBaseLabel extends ServoyBootstrapBaseComponent {

    @Input() imageStyleClass;
    @Input() showAs;

    @ViewChild('child', {static: true}) child:ElementRef;

    constructor(renderer: Renderer2) {
        super(renderer);
    }

    public getNativeChild() {
        return this.child.nativeElement;
    }

    isTrustedHTML(): boolean {
        if(this.servoyApi.trustAsHtml() || this.showAs === 'trusted_html') {
            return true;
        }
        return false;
    }
}