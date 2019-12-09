import { async, ComponentFixture, TestBed } from '@angular/core/testing';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';

import { FormComponent,AddAttributeDirective } from './form_component.component';

import {FormService} from '../form.service';
import {ServoyService} from '../servoy.service';
import {SabloService} from '../../sablo/sablo.service';

import { ErrorBean } from '../../servoycore/error-bean/error-bean';
import { ServoyDefaultComponentsModule } from '../../servoydefault/servoydefault.module';
import {ServoyCoreSlider} from '../../servoycore/slider/slider';

describe('FormComponent', () => {
  let component: FormComponent;
  let fixture: ComponentFixture<FormComponent>;
  let sabloService;
  let formService;
  let servoyService;

  beforeEach(async(() => {
      sabloService = jasmine.createSpyObj("SabloService", ["callService"]);
      formService = jasmine.createSpyObj("FormService", {getFormCache:{absolute:true,getComponent:()=>null}, destroy:()=>null});
      servoyService = jasmine.createSpyObj("ServoyService", ["connect"]);
    TestBed.configureTestingModule({
      declarations: [ FormComponent,AddAttributeDirective,ServoyCoreSlider,ErrorBean ],
      imports: [
                ServoyDefaultComponentsModule
       ],
       providers:    [ {provide: FormService, useValue:  formService },
                               {provide: SabloService, useValue:  sabloService },
                               {provide: ServoyService, useValue:  servoyService }
                             ],
       schemas: [
              CUSTOM_ELEMENTS_SCHEMA
       ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
    expect(formService.getFormCache).toHaveBeenCalled();
  });
});
