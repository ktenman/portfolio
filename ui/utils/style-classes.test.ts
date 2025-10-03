import { describe, it, expect } from 'vitest'
import { styleClasses } from './style-classes'

describe('styleClasses', () => {
  describe('layout', () => {
    it('should generate responsive column classes', () => {
      expect(styleClasses.layout.colMd(6)).toBe('col-md-6')
      expect(styleClasses.layout.colMd(12)).toBe('col-md-12')
      expect(styleClasses.layout.colMd(4)).toBe('col-md-4')
    })

    it('should generate large column classes', () => {
      expect(styleClasses.layout.colLg(6)).toBe('col-lg-6')
      expect(styleClasses.layout.colLg(8)).toBe('col-lg-8')
      expect(styleClasses.layout.colLg(3)).toBe('col-lg-3')
    })

    it('should have static layout classes', () => {
      expect(styleClasses.layout.container).toBe('container')
      expect(styleClasses.layout.containerFluid).toBe('container-fluid')
      expect(styleClasses.layout.row).toBe('row')
      expect(styleClasses.layout.col).toBe('col')
      expect(styleClasses.layout.colAuto).toBe('col-auto')
    })
  })

  describe('spacing', () => {
    it('should generate margin top classes', () => {
      expect(styleClasses.spacing.mt(0)).toBe('mt-0')
      expect(styleClasses.spacing.mt(3)).toBe('mt-3')
      expect(styleClasses.spacing.mt(5)).toBe('mt-5')
    })

    it('should generate margin bottom classes', () => {
      expect(styleClasses.spacing.mb(1)).toBe('mb-1')
      expect(styleClasses.spacing.mb(4)).toBe('mb-4')
    })

    it('should generate margin y-axis classes', () => {
      expect(styleClasses.spacing.my(2)).toBe('my-2')
      expect(styleClasses.spacing.my(3)).toBe('my-3')
    })

    it('should generate margin x-axis classes', () => {
      expect(styleClasses.spacing.mx(2)).toBe('mx-2')
      expect(styleClasses.spacing.mx(4)).toBe('mx-4')
    })

    it('should generate margin start/end classes', () => {
      expect(styleClasses.spacing.ms(1)).toBe('ms-1')
      expect(styleClasses.spacing.me(2)).toBe('me-2')
    })

    it('should generate padding classes', () => {
      expect(styleClasses.spacing.p(0)).toBe('p-0')
      expect(styleClasses.spacing.p(3)).toBe('p-3')
    })

    it('should generate padding y-axis classes', () => {
      expect(styleClasses.spacing.py(2)).toBe('py-2')
      expect(styleClasses.spacing.py(4)).toBe('py-4')
    })

    it('should generate padding x-axis classes', () => {
      expect(styleClasses.spacing.px(1)).toBe('px-1')
      expect(styleClasses.spacing.px(3)).toBe('px-3')
    })

    it('should generate padding start/end classes', () => {
      expect(styleClasses.spacing.ps(1)).toBe('ps-1')
      expect(styleClasses.spacing.pe(2)).toBe('pe-2')
    })
  })

  describe('display', () => {
    it('should have display classes', () => {
      expect(styleClasses.display.flex).toBe('d-flex')
      expect(styleClasses.display.none).toBe('d-none')
      expect(styleClasses.display.block).toBe('d-block')
      expect(styleClasses.display.inline).toBe('d-inline')
      expect(styleClasses.display.inlineBlock).toBe('d-inline-block')
      expect(styleClasses.display.mdInline).toBe('d-md-inline')
      expect(styleClasses.display.mdNone).toBe('d-md-none')
    })
  })

  describe('flexbox', () => {
    it('should have flexbox utility classes', () => {
      expect(styleClasses.flexbox.justifyBetween).toBe('justify-content-between')
      expect(styleClasses.flexbox.justifyCenter).toBe('justify-content-center')
      expect(styleClasses.flexbox.alignCenter).toBe('align-items-center')
      expect(styleClasses.flexbox.alignEnd).toBe('align-items-end')
      expect(styleClasses.flexbox.flexColumn).toBe('flex-column')
      expect(styleClasses.flexbox.flexGrow1).toBe('flex-grow-1')
    })
  })

  describe('text', () => {
    it('should have text alignment classes', () => {
      expect(styleClasses.text.center).toBe('text-center')
      expect(styleClasses.text.start).toBe('text-start')
      expect(styleClasses.text.end).toBe('text-end')
    })

    it('should have text color classes', () => {
      expect(styleClasses.text.primary).toBe('text-primary')
      expect(styleClasses.text.secondary).toBe('text-secondary')
      expect(styleClasses.text.success).toBe('text-success')
      expect(styleClasses.text.danger).toBe('text-danger')
      expect(styleClasses.text.warning).toBe('text-warning')
      expect(styleClasses.text.info).toBe('text-info')
      expect(styleClasses.text.muted).toBe('text-muted')
    })

    it('should have text formatting classes', () => {
      expect(styleClasses.text.nowrap).toBe('text-nowrap')
    })
  })

  describe('buttons', () => {
    it('should have button base classes', () => {
      expect(styleClasses.buttons.base).toBe('btn')
      expect(styleClasses.buttons.primary).toBe('btn btn-primary')
      expect(styleClasses.buttons.secondary).toBe('btn btn-secondary')
      expect(styleClasses.buttons.danger).toBe('btn btn-danger')
      expect(styleClasses.buttons.success).toBe('btn btn-success')
    })

    it('should have outline button classes', () => {
      expect(styleClasses.buttons.outline.primary).toBe('btn btn-outline-primary')
      expect(styleClasses.buttons.outline.secondary).toBe('btn btn-outline-secondary')
      expect(styleClasses.buttons.outline.danger).toBe('btn btn-outline-danger')
    })

    it('should have ghost button classes', () => {
      expect(styleClasses.buttons.ghost.primary).toBe('btn btn-ghost btn-primary')
      expect(styleClasses.buttons.ghost.secondary).toBe('btn btn-ghost btn-secondary')
      expect(styleClasses.buttons.ghost.danger).toBe('btn btn-ghost btn-danger')
    })

    it('should have button size classes', () => {
      expect(styleClasses.buttons.sm).toBe('btn-sm')
      expect(styleClasses.buttons.lg).toBe('btn-lg')
    })

    it('should have button close class', () => {
      expect(styleClasses.buttons.close).toBe('btn-close')
    })
  })

  describe('forms', () => {
    it('should have form control classes', () => {
      expect(styleClasses.forms.control).toBe('form-control')
      expect(styleClasses.forms.select).toBe('form-select')
      expect(styleClasses.forms.label).toBe('form-label')
      expect(styleClasses.forms.text).toBe('form-text')
    })

    it('should have form check classes', () => {
      expect(styleClasses.forms.check.input).toBe('form-check-input')
      expect(styleClasses.forms.check.label).toBe('form-check-label')
    })

    it('should have floating form classes', () => {
      expect(styleClasses.forms.floating.container).toBe('form-floating')
    })

    it('should have form validation classes', () => {
      expect(styleClasses.forms.validation.invalid).toBe('is-invalid')
      expect(styleClasses.forms.validation.valid).toBe('is-valid')
      expect(styleClasses.forms.validation.feedback).toBe('invalid-feedback')
    })
  })

  describe('table', () => {
    it('should have table classes', () => {
      expect(styleClasses.table.base).toBe('table')
      expect(styleClasses.table.striped).toBe('table table-striped')
      expect(styleClasses.table.hover).toBe('table table-hover')
      expect(styleClasses.table.bordered).toBe('table table-bordered')
      expect(styleClasses.table.responsive).toBe('table-responsive')
    })
  })

  describe('alerts', () => {
    it('should have alert classes', () => {
      expect(styleClasses.alerts.base).toBe('alert')
      expect(styleClasses.alerts.danger).toBe('alert alert-danger')
      expect(styleClasses.alerts.info).toBe('alert alert-info')
      expect(styleClasses.alerts.success).toBe('alert alert-success')
      expect(styleClasses.alerts.warning).toBe('alert alert-warning')
    })
  })

  describe('modal', () => {
    it('should have modal classes', () => {
      expect(styleClasses.modal.backdrop).toBe('modal-backdrop')
      expect(styleClasses.modal.dialog).toBe('modal-dialog')
      expect(styleClasses.modal.content).toBe('modal-content')
      expect(styleClasses.modal.header).toBe('modal-header')
      expect(styleClasses.modal.body).toBe('modal-body')
      expect(styleClasses.modal.footer).toBe('modal-footer')
      expect(styleClasses.modal.title).toBe('modal-title')
      expect(styleClasses.modal.centered).toBe('modal-dialog-centered')
    })
  })

  describe('utilities', () => {
    it('should have utility classes', () => {
      expect(styleClasses.utilities.visuallyHidden).toBe('visually-hidden')
      expect(styleClasses.utilities.textTruncate).toBe('text-truncate')
      expect(styleClasses.utilities.userSelectNone).toBe('user-select-none')
      expect(styleClasses.utilities.shadow).toBe('shadow')
      expect(styleClasses.utilities.shadowSm).toBe('shadow-sm')
      expect(styleClasses.utilities.rounded).toBe('rounded')
      expect(styleClasses.utilities.border).toBe('border')
      expect(styleClasses.utilities.borderTop).toBe('border-top')
      expect(styleClasses.utilities.borderBottom).toBe('border-bottom')
    })
  })

  describe('spinner', () => {
    it('should have spinner classes', () => {
      expect(styleClasses.spinner.border).toBe('spinner-border')
      expect(styleClasses.spinner.borderSm).toBe('spinner-border spinner-border-sm')
    })
  })

  describe('navigation', () => {
    it('should have navigation classes', () => {
      expect(styleClasses.navigation.navbar).toBe('navbar')
      expect(styleClasses.navigation.nav).toBe('nav')
      expect(styleClasses.navigation.navLink).toBe('nav-link')
      expect(styleClasses.navigation.navItem).toBe('nav-item')
    })
  })

  describe('badge', () => {
    it('should have badge classes', () => {
      expect(styleClasses.badge.base).toBe('badge')
      expect(styleClasses.badge.primary).toBe('badge bg-primary')
      expect(styleClasses.badge.secondary).toBe('badge bg-secondary')
      expect(styleClasses.badge.success).toBe('badge bg-success')
      expect(styleClasses.badge.danger).toBe('badge bg-danger')
    })
  })
})
