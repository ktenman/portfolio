export const styleClasses = {
  layout: {
    container: 'container',
    containerFluid: 'container-fluid',
    row: 'row',
    col: 'col',
    colAuto: 'col-auto',
    colMd: (size: number) => `col-md-${size}`,
    colLg: (size: number) => `col-lg-${size}`,
  },

  spacing: {
    mt: (size: number) => `mt-${size}`,
    mb: (size: number) => `mb-${size}`,
    my: (size: number) => `my-${size}`,
    ms: (size: number) => `ms-${size}`,
    me: (size: number) => `me-${size}`,
    mx: (size: number) => `mx-${size}`,
    p: (size: number) => `p-${size}`,
    py: (size: number) => `py-${size}`,
    px: (size: number) => `px-${size}`,
    ps: (size: number) => `ps-${size}`,
    pe: (size: number) => `pe-${size}`,
  },

  display: {
    flex: 'd-flex',
    none: 'd-none',
    block: 'd-block',
    inline: 'd-inline',
    inlineBlock: 'd-inline-block',
    mdInline: 'd-md-inline',
    mdNone: 'd-md-none',
  },

  flexbox: {
    justifyBetween: 'justify-content-between',
    justifyCenter: 'justify-content-center',
    alignCenter: 'align-items-center',
    alignEnd: 'align-items-end',
    flexColumn: 'flex-column',
    flexGrow1: 'flex-grow-1',
  },

  text: {
    center: 'text-center',
    start: 'text-start',
    end: 'text-end',
    primary: 'text-primary',
    secondary: 'text-secondary',
    success: 'text-success',
    danger: 'text-danger',
    warning: 'text-warning',
    info: 'text-info',
    muted: 'text-muted',
    nowrap: 'text-nowrap',
  },

  buttons: {
    base: 'btn',
    primary: 'btn btn-primary',
    secondary: 'btn btn-secondary',
    danger: 'btn btn-danger',
    success: 'btn btn-success',
    outline: {
      primary: 'btn btn-outline-primary',
      secondary: 'btn btn-outline-secondary',
      danger: 'btn btn-outline-danger',
    },
    ghost: {
      primary: 'btn btn-ghost btn-primary',
      secondary: 'btn btn-ghost btn-secondary',
      danger: 'btn btn-ghost btn-danger',
    },
    sm: 'btn-sm',
    lg: 'btn-lg',
    close: 'btn-close',
  },

  forms: {
    control: 'form-control',
    select: 'form-select',
    label: 'form-label',
    text: 'form-text',
    check: {
      input: 'form-check-input',
      label: 'form-check-label',
    },
    floating: {
      container: 'form-floating',
    },
    validation: {
      invalid: 'is-invalid',
      valid: 'is-valid',
      feedback: 'invalid-feedback',
    },
  },

  table: {
    base: 'table',
    striped: 'table table-striped',
    hover: 'table table-hover',
    bordered: 'table table-bordered',
    responsive: 'table-responsive',
  },

  alerts: {
    base: 'alert',
    danger: 'alert alert-danger',
    info: 'alert alert-info',
    success: 'alert alert-success',
    warning: 'alert alert-warning',
  },

  modal: {
    backdrop: 'modal-backdrop',
    dialog: 'modal-dialog',
    content: 'modal-content',
    header: 'modal-header',
    body: 'modal-body',
    footer: 'modal-footer',
    title: 'modal-title',
    centered: 'modal-dialog-centered',
  },

  utilities: {
    visuallyHidden: 'visually-hidden',
    textTruncate: 'text-truncate',
    userSelectNone: 'user-select-none',
    shadow: 'shadow',
    shadowSm: 'shadow-sm',
    rounded: 'rounded',
    border: 'border',
    borderTop: 'border-top',
    borderBottom: 'border-bottom',
  },

  spinner: {
    border: 'spinner-border',
    borderSm: 'spinner-border spinner-border-sm',
  },

  navigation: {
    navbar: 'navbar',
    nav: 'nav',
    navLink: 'nav-link',
    navItem: 'nav-item',
  },

  badge: {
    base: 'badge',
    primary: 'badge bg-primary',
    secondary: 'badge bg-secondary',
    success: 'badge bg-success',
    danger: 'badge bg-danger',
  },
} as const
