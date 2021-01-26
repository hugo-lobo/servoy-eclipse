import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { ServoyExtraTextfieldGroup  } from './textfieldgroup';
import { FormattingService, ServoyApi, TooltipService } from '../../ngclient/servoy_public';
import { SabloModule } from '../../sablo/sablo.module';
import { FormsModule } from '@angular/forms';
import { ServoyPublicModule } from '../../ngclient/servoy_public.module';
import { By } from '@angular/platform-browser';
import { AngularEditorModule } from '@kolkov/angular-editor';
import { HttpClientModule } from '@angular/common/http';

describe('TextfieldgroupComponent', () => {
  let component: ServoyExtraTextfieldGroup;
  let fixture: ComponentFixture<ServoyExtraTextfieldGroup>;

  const servoyApi: jasmine.SpyObj<ServoyApi> = jasmine.createSpyObj<ServoyApi>('ServoyApi', ['getMarkupId', 'isInDesigner','registerComponent','unRegisterComponent']);

    beforeEach(waitForAsync(() => {

    TestBed.configureTestingModule({
      declarations: [ ServoyExtraTextfieldGroup ],
      imports: [SabloModule, FormsModule, AngularEditorModule, HttpClientModule, ServoyPublicModule],
      providers: [FormattingService, TooltipService]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ServoyExtraTextfieldGroup);

    fixture.componentInstance.servoyApi = servoyApi;
    component = fixture.componentInstance;
    component.dataProviderID = 'WhatArea';
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

});
