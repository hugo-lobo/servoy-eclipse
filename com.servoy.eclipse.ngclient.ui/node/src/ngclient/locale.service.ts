import { Injectable } from '@angular/core';
import { SabloService } from '../sablo/sablo.service';
import { Deferred, SessionStorageService, LoggerFactory, LoggerService, Locale } from '@servoy/public';
import { registerLocaleData } from '@angular/common';

import numbro from 'numbro';
import { Settings } from 'luxon';

import { I18NProvider } from './services/i18n_provider.service';

@Injectable({
  providedIn: 'root'
})
export class LocaleService {
    private locale = 'en';
    private loadedLocale: Deferred<any>;

    private readonly localeMap = { en: 'en-US' };
    private readonly log: LoggerService;

    constructor(private sabloService: SabloService,
        private i18nProvider: I18NProvider,
        private sessionStorageService: SessionStorageService,
        logFactory: LoggerFactory ) {
            this.log = logFactory.getLogger('LocaleService');
    }

    public isLoaded(): Promise<any> {
        return this.loadedLocale.promise;
    }

    public getLocale(): string {
        return this.locale;
    }

    public getLocaleObject(): Locale {
        return this.sabloService.getLocale();;
    }

    public setLocale(language: string, country: string, initializing?: boolean) {
        // TODO angular $translate and our i18n service
        //            $translate.refresh();
        this.loadedLocale = new Deferred<any>();
        this.setAngularLocale(language, country).then(localeId => {
            this.i18nProvider.flush();
            this.sabloService.setLocale({ language, country, full: localeId });
            if (!initializing) {
                // in the session storage we always have the value set via applicationService.setLocale
                this.sessionStorageService.set('locale', language + '-' + country);
            }
            // luxon default locale
            Settings.defaultLocale =  localeId;
            this.locale = localeId;
            const full = language + (country ? '-' + country.toUpperCase() : '');
            // numbro wants with upper case counter but moment is all lower case
            this.setNumbroLocale(full, true).then(() =>
                this.loadedLocale.resolve(localeId)
            ).catch(() => this.loadedLocale.resolve(localeId));
        }, () => {
            this.loadedLocale.reject('Could not set Locale because angular locale could not be loaded.');
        });
    }

    private makeFullLocale(localeId: string): string {
        let locale = this.localeMap[localeId];
        if (!locale) locale = localeId + '-' + localeId.toUpperCase();
        return locale;
    }

    private setNumbroLocale(localeId: string, tryOnlyLanguage: boolean): Promise<void> {
        if (numbro.language() === localeId) return Promise.resolve();
        return import(`numbro/languages/${localeId}`).then(module => {
            numbro.registerLanguage(module.default);
            numbro.setLanguage(localeId);
        }).catch(e => {
            const index = localeId.indexOf('-');
            if (index === -1) {
                return this.setNumbroLocale(this.makeFullLocale(localeId), false);
            } else if (tryOnlyLanguage) {
                return this.setNumbroLocale(localeId.substring(0, index), false);
            } else {
                this.log.warn('numbro locale for ' + localeId + ' didn\'t resolve, fallback to default en-US');
            }
        });
    }

    private setAngularLocale(language: string, country: string) {
        // angular locales are either <language lowercase> or <language lowercase> - <country uppercase>
        const localeId = country !== undefined && country.length > 0 ?
            language.toLowerCase() + '-' + country.toUpperCase() : language.toLowerCase();
        return new Promise<string>((resolve, reject) => {
            import(
                `../../node_modules/@angular/common/locales/${localeId}.mjs`).then(
                module => {
                    registerLocaleData(module.default, localeId);
                    resolve(localeId);
                },
                () => {
                    import(`../../node_modules/@angular/common/locales/${language.toLowerCase()}.mjs`).then(module => {
                        registerLocaleData(module.default, localeId.split('-')[0]);
                        resolve(language.toLowerCase());
                    }, reject);
                });
        });
    }
}
