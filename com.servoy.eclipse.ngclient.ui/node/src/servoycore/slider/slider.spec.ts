import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SabloModule } from '../../sablo/sablo.module'
import { ServoyPublicModule } from '../../ngclient/servoy_public.module'

import { ServoyCoreSlider } from './slider';
import { TooltipService } from '../../ngclient/servoy_public'


describe("ServoyCoreSlider", () => {
  let component: ServoyCoreSlider;
  let fixture: ComponentFixture<ServoyCoreSlider>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ServoyCoreSlider],
      imports: [SabloModule, ServoyPublicModule],
      providers: [TooltipService]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ServoyCoreSlider);
    component = fixture.componentInstance;
    component.servoyApi =  jasmine.createSpyObj("ServoyApi", ["getMarkupId","trustAsHtml"]);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
