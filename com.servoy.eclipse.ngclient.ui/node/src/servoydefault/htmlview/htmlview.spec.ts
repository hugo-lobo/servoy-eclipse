import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { ServoyTestingModule } from '../../testing/servoytesting.module';
import { ServoyPublicModule } from 'servoy-public';

import { ServoyDefaultHTMLView } from './htmlview';

import { FormattingService, TooltipService, ServoyApi } from 'servoy-public';
import { By } from '@angular/platform-browser';

import { runOnPushChangeDetection } from '../../testing';

describe('ServoyDefaultHTMLView', () => {
  let component: ServoyDefaultHTMLView;
  let fixture: ComponentFixture<ServoyDefaultHTMLView>;
  const servoyApi: jasmine.SpyObj<ServoyApi> = jasmine.createSpyObj<ServoyApi>('ServoyApi', ['getMarkupId','trustAsHtml','registerComponent','unRegisterComponent','registerComponent','unRegisterComponent']);

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [ ServoyDefaultHTMLView],
      imports: [ServoyTestingModule, ServoyPublicModule ],
      providers: [FormattingService, TooltipService]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ServoyDefaultHTMLView);
    component = fixture.componentInstance;
    component.servoyApi =  servoyApi;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it( 'should render markupid ', () => {
      servoyApi.getMarkupId.and.returnValue( 'myid');
      runOnPushChangeDetection(fixture);
      const div = fixture.debugElement.query(By.css('div')).nativeElement;
      expect(div.id).toBe('myid');
    });

  it( 'should have called servoyApi.getMarkupId', () => {
      expect( component.servoyApi.getMarkupId ).toHaveBeenCalled();
  });

  it ('should test innerhtml', () => {
      component.dataProviderID = '<p>some text herre</p>';
       runOnPushChangeDetection(fixture);
      const spanEl = fixture.debugElement.query(By.css('span'));
      expect(spanEl.nativeElement.innerHTML).toBe('<p>some text herre</p>');
      expect(component.servoyApi.trustAsHtml).toHaveBeenCalled();
  });
});
