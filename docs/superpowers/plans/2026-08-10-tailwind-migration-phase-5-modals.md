# Tailwind Migration Phase 5 (Modals) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Bootstrap's `Modal` JavaScript with one native `<dialog>` shell controlled by `v-model:open`, and migrate all six modals onto it.

**Architecture:** A single `modal-shell.vue` owns the `<dialog>` element, the open/close lifecycle, the backdrop, and the body scroll lock. It keeps Bootstrap's _component_ classes (`.modal-dialog`, `.modal-content`, `.modal-header`, `.modal-title`, `.modal-body`, `.modal-footer`, `.btn-close`) so appearance is unchanged — only the JS and the outer element change. Each of the six modals becomes a thin consumer that supplies a title, a body slot, and a footer slot.

**Tech Stack:** Vue 3.5 (`<script setup>`, `defineModel`-style `v-model:open`), TypeScript strict, Vitest + happy-dom (native `<dialog>` support confirmed), Playwright pixel gate.

## Global Constraints

- **Any non-zero screenshot diff is a regression.** Fix it, or record it in the PR as accepted drift with rationale. Never accept silently.
- **Tailwind uses the `tw:` prefix until Phase 9.** Utilities are scanned as _literal_ strings — a computed class name is never emitted.
- **Bootstrap component classes stay until Phase 8/9.** Translate utilities only; do not restyle.
- **Bootstrap utilities carry `!important`.** Add `!` whenever a scoped or global rule of higher specificity sets the same property.
- **No code comments.** Self-documenting names only.
- **No AI attribution** in commits or PR descriptions.
- **Commit subjects:** uppercase imperative verb, ≤50 chars, no `feat:`/`fix:`/`chore:` prefixes.
- **Branch:** `feature/1671-tailwind-phase-5-modals`, branched from `main`. Never commit to `main`.
- **PR body ends with `Refs #1662`**, and `Closes #1671`.
- `npm run lint-format` strips `ui/models/generated/domain-models.ts` via `eslint --fix`. Always `git checkout ui/models/generated/domain-models.ts` afterwards.
- Known pre-existing visual failure: `state loading skeleton` (desktop, 13658 px) also fails on `main`. Not caused by this work.
- **The `modal confirm` visual capture is destructive.** Its confirm button deletes all portfolio summary data. The test dismisses via `[data-testid="confirmDialogCancelButton"]` and installs `page.route('**/api/portfolio-summary/recalculate**', route => route.abort())`. **Never click confirm; never remove the route abort.**

---

## Behaviour Inventory (what must be preserved)

Established by reading all six modals. Two different Esc behaviours exist today — this is why the shell needs a `closeOnEsc` prop.

| Modal                    | Opened by                                                         | Esc closes? | Backdrop click closes?  |
| ------------------------ | ----------------------------------------------------------------- | ----------- | ----------------------- |
| `instrument-modal`       | `useBootstrapModal('instrumentModal')` → `new Modal(el)` defaults | **yes**     | yes                     |
| `xirr-windows-modal`     | `useBootstrapModal('xirrWindowsModal')` defaults                  | **yes**     | yes                     |
| `annual-windows-modal`   | `useBootstrapModal('annualWindowsModal')` defaults                | **yes**     | yes                     |
| `confirm-dialog`         | own `new Modal(el, {backdrop:'static', keyboard:false})`          | **no**      | yes (via `@click.self`) |
| `logo-replacement-modal` | same static options                                               | **no**      | yes (via `@click.self`) |
| `config-dialog`          | same static options                                               | **no**      | yes (via `@click.self`) |

Backdrop click closes all six, so the shell handles it unconditionally. Esc differs, so it is a prop defaulting to `true`.

`xirr-windows-modal` and `annual-windows-modal` already take an `open` prop, but today it only triggers **data loading** — visibility comes from the composable. After this phase the same prop drives both.

## Two Findings Not In Issue #1671

**1. The visual harness is itself coupled to Bootstrap internals.** `ui/tests/visual/states.spec.ts` depends on three things native `<dialog>` does not provide:

- `:36` `page.locator('.modal-backdrop.show')` — Bootstrap JS injects that DOM node. `::backdrop` is a pseudo-element with **no node**, so this locator can never match.
- `:44`, `:46`, `:48`, `:154` — `.modal.show` — the `show` class is added by Bootstrap JS. Native dialogs use the `open` attribute.
- `:62-72` `openInstrumentModal` synthesises Bootstrap's declarative data-API by creating a `<div data-bs-toggle="modal" data-bs-target="#instrumentModal">` and clicking it.

Task 2 makes these selectors tolerate both worlds so the gate stays green during migration; Task 9 tightens them.

**2. Bootstrap locks body scroll; native `<dialog>` does not.** Bootstrap sets `overflow: hidden` **and** `padding-right: <scrollbar width>` on `<body>` while a modal is open. The backdrop is 50% transparent, so page content behind it is visible in every modal screenshot. Dropping the padding compensation would shift that content and produce a non-zero diff. The shell therefore reproduces the lock.

---

## File Structure

**Create**

- `ui/components/shared/modal-shell.vue` — the `<dialog>` element, open/close lifecycle, `::backdrop`, scroll lock, header/body/footer slots. The only file that touches `showModal`/`close`.
- `ui/components/shared/modal-shell.test.ts` — shell behaviour: open/close via prop, Esc honoured and suppressed, backdrop click, scroll lock, close event round-trip.

**Modify**

- `ui/components/shared/confirm-dialog.vue` (121 lines) — drop `new Modal`, consume shell.
- `ui/components/instruments/instrument-modal.vue` (60) — drop `modalId` prop, gain `open`, consume shell.
- `ui/components/instruments/xirr-windows-modal.vue` (112) — `open` prop now drives visibility as well as loading.
- `ui/components/instruments/annual-windows-modal.vue` (112) — same.
- `ui/components/etf/logo-replacement-modal.vue` (227) — drop `new Modal`, consume shell.
- `ui/components/diversification/config-dialog.vue` (294) — drop `new Modal`, consume shell.
- `ui/components/instruments/instruments-view.vue` (362) — replace three `useBootstrapModal` calls with three `ref`s.
- `ui/tests/visual/states.spec.ts` — decouple from Bootstrap JS internals.

**Delete**

- `ui/composables/use-bootstrap-modal.ts` (48)
- `ui/composables/use-bootstrap-modal.test.ts` (172)

**Rewrite (coupled tests)**

- `ui/components/shared/confirm-dialog.test.ts` (179) — currently `vi.mock('bootstrap')`.
- `ui/components/diversification/config-dialog.test.ts` (243)
- `ui/components/instruments/instrument-modal.test.ts` (142)

---

## Task 1: The `<dialog>` shell

**Files:**

- Create: `ui/components/shared/modal-shell.vue`
- Test: `ui/components/shared/modal-shell.test.ts`

**Interfaces:**

- Consumes: nothing.
- Produces: `<modal-shell>` with props `open: boolean` (required), `modalId: string` (required), `title?: string` (default `''`), `size?: 'lg'`, `centered?: boolean` (default `false`), `closeOnEsc?: boolean` (default `true`), `bodyClass?: string` (default `''`); emit `update:open: [value: boolean]`; slots `default` (body), `footer`, `title`.

- [ ] **Step 1: Write the failing test**

Create `ui/components/shared/modal-shell.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ModalShell from './modal-shell.vue'

const mountShell = (props = {}) =>
  mount(ModalShell, {
    props: { open: false, modalId: 'testModal', title: 'Tähtis päring', ...props },
    attachTo: document.body,
  })

describe('ModalShell', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    document.body.style.overflow = ''
    document.body.style.paddingRight = ''
  })

  it('stays closed when open is false', () => {
    const wrapper = mountShell()

    expect(wrapper.find('dialog').element.open).toBe(false)
  })

  it('opens the native dialog when open becomes true', async () => {
    const wrapper = mountShell()

    await wrapper.setProps({ open: true })

    expect(wrapper.find('dialog').element.open).toBe(true)
  })

  it('opens the native dialog when mounted already open', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('dialog').element.open).toBe(true)
  })

  it('closes the native dialog when open becomes false', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.setProps({ open: false })

    expect(wrapper.find('dialog').element.open).toBe(false)
  })

  it('emits update:open false when the dialog closes natively', async () => {
    const wrapper = mountShell({ open: true })

    wrapper.find('dialog').element.close()
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('closes when the close button is clicked', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.find('.btn-close').trigger('click')

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('closes when the backdrop is clicked', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.find('dialog').trigger('click')

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })

  it('dont close when the dialog content is clicked', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.find('.modal-content').trigger('click')

    expect(wrapper.emitted('update:open')).toBeFalsy()
  })

  it('suppresses escape when closeOnEsc is false', async () => {
    const wrapper = mountShell({ open: true, closeOnEsc: false })

    const event = new Event('cancel', { cancelable: true })
    wrapper.find('dialog').element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(event.defaultPrevented).toBe(true)
  })

  it('allows escape when closeOnEsc is true', async () => {
    const wrapper = mountShell({ open: true })

    const event = new Event('cancel', { cancelable: true })
    wrapper.find('dialog').element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(event.defaultPrevented).toBe(false)
  })

  it('locks body scroll while open', async () => {
    const wrapper = mountShell()

    await wrapper.setProps({ open: true })

    expect(document.body.style.overflow).toBe('hidden')
  })

  it('restores body scroll when closed', async () => {
    const wrapper = mountShell({ open: true })

    await wrapper.setProps({ open: false })

    expect(document.body.style.overflow).toBe('')
  })

  it('restores body scroll when unmounted while open', () => {
    const wrapper = mountShell({ open: true })

    wrapper.unmount()

    expect(document.body.style.overflow).toBe('')
  })

  it('renders the title into the modal title element', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('.modal-title').text()).toBe('Tähtis päring')
  })

  it('links the title to the dialog for screen readers', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('dialog').attributes('aria-labelledby')).toBe('testModalLabel')
    expect(wrapper.find('.modal-title').attributes('id')).toBe('testModalLabel')
  })

  it('renders the default slot into the modal body', () => {
    const wrapper = mount(ModalShell, {
      props: { open: true, modalId: 'testModal' },
      slots: { default: '<p class="probe">Sisu</p>' },
      attachTo: document.body,
    })

    expect(wrapper.find('.modal-body .probe').text()).toBe('Sisu')
  })

  it('renders the footer slot into the modal footer', () => {
    const wrapper = mount(ModalShell, {
      props: { open: true, modalId: 'testModal' },
      slots: { footer: '<button class="probe">Sulge</button>' },
      attachTo: document.body,
    })

    expect(wrapper.find('.modal-footer .probe').text()).toBe('Sulge')
  })

  it('applies the large size class to the dialog wrapper', () => {
    const wrapper = mountShell({ open: true, size: 'lg' })

    expect(wrapper.find('.modal-dialog').classes()).toContain('modal-lg')
  })

  it('applies the centered class to the dialog wrapper', () => {
    const wrapper = mountShell({ open: true, centered: true })

    expect(wrapper.find('.modal-dialog').classes()).toContain('modal-dialog-centered')
  })

  it('dont apply the fade class that would zero the opacity', () => {
    const wrapper = mountShell({ open: true })

    expect(wrapper.find('dialog').classes()).not.toContain('fade')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx vitest run ui/components/shared/modal-shell.test.ts`
Expected: FAIL — `Failed to resolve import "./modal-shell.vue"`

- [ ] **Step 3: Write the shell**

Create `ui/components/shared/modal-shell.vue`:

```vue
<template>
  <dialog
    ref="dialogEl"
    :id="modalId"
    class="modal"
    :aria-labelledby="`${modalId}Label`"
    @click.self="requestClose"
    @cancel="onCancel"
    @close="onClose"
  >
    <div class="modal-dialog" :class="dialogClasses">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h5 class="modal-title" :id="`${modalId}Label`">
            <slot name="title">{{ title }}</slot>
          </h5>
          <button type="button" class="btn-close" aria-label="Close" @click="requestClose"></button>
        </div>
        <div class="modal-body" :class="bodyClass">
          <slot />
        </div>
        <div class="modal-footer">
          <slot name="footer" />
        </div>
      </div>
    </div>
  </dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

interface Props {
  open: boolean
  modalId: string
  title?: string
  size?: 'lg'
  centered?: boolean
  closeOnEsc?: boolean
  bodyClass?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  size: undefined,
  centered: false,
  closeOnEsc: true,
  bodyClass: '',
})

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const dialogEl = ref<HTMLDialogElement | null>(null)

const dialogClasses = computed(() => ({
  'modal-lg': props.size === 'lg',
  'modal-dialog-centered': props.centered,
}))

const lockScroll = () => {
  const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth
  document.body.style.overflow = 'hidden'
  if (scrollbarWidth > 0) document.body.style.paddingRight = `${scrollbarWidth}px`
}

const unlockScroll = () => {
  document.body.style.overflow = ''
  document.body.style.paddingRight = ''
}

const requestClose = () => {
  dialogEl.value?.close()
}

const onCancel = (event: Event) => {
  if (!props.closeOnEsc) event.preventDefault()
}

const onClose = () => {
  unlockScroll()
  emit('update:open', false)
}

watch(
  () => props.open,
  isOpen => {
    const dialog = dialogEl.value
    if (!dialog) return
    if (isOpen && !dialog.open) {
      dialog.showModal()
      lockScroll()
      return
    }
    if (!isOpen && dialog.open) dialog.close()
  },
  { immediate: true, flush: 'post' }
)

onBeforeUnmount(() => {
  unlockScroll()
})
</script>

<style scoped>
dialog.modal {
  display: none;
  max-width: none;
  max-height: none;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
}

dialog.modal[open] {
  display: block;
}

dialog.modal::backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}
</style>
```

Three details that matter:

1. **No `fade` class.** Bootstrap's `.fade { opacity: 0 }` only becomes visible via `.show`, which Bootstrap JS adds. Rendering `fade` without `show` would leave every modal invisible. Omitting both leaves opacity at 1 and `.modal-dialog` untransformed — identical to Bootstrap's _end_ state, which is what the pixel gate captures (`animations: 'disabled'`).
2. **`dialog.modal[open]` beats `.modal`.** Bootstrap's `.modal { display: none }` (0,1,0) would otherwise defeat the UA's `dialog[open]` rule, since author styles outrank the UA sheet. `dialog.modal[open]` is (0,2,0) and wins.
3. **`flush: 'post'` + `immediate: true`** so a modal mounted with `open: true` still opens — the template ref is not populated until after mount.

- [ ] **Step 4: Run the test to verify it passes**

Run: `npx vitest run ui/components/shared/modal-shell.test.ts`
Expected: PASS, 20 tests.

- [ ] **Step 5: Commit**

```bash
git add ui/components/shared/modal-shell.vue ui/components/shared/modal-shell.test.ts
git commit -m "Add native dialog modal shell"
```

---

## Task 2: Make the visual harness framework-agnostic

Do this **before** migrating any modal, so the pixel gate stays green at every commit instead of going red for six commits and back.

**Files:**

- Modify: `ui/tests/visual/states.spec.ts:35-51`

**Interfaces:**

- Consumes: `modal-shell.vue` renders `<dialog class="modal">` with the `open` attribute when shown.
- Produces: `waitForModal(page, title)` that works for both Bootstrap-JS modals and native dialogs.

- [ ] **Step 1: Confirm the gate is green before touching it**

```bash
npx playwright test --grep "modal"
```

Expected: 7 modal tests pass (tablet project skipped). If any fail before you change anything, stop and investigate — you need a trustworthy starting point.

- [ ] **Step 2: Replace the two helpers**

In `ui/tests/visual/states.spec.ts`, replace `waitForBackdropToSettle` and `waitForModal` (lines 35-51) with:

```ts
const OPEN_MODAL = ':is(.modal.show, dialog.modal[open])'

async function waitForBackdropToSettle(page: Page): Promise<void> {
  const bootstrapBackdrop = page.locator('.modal-backdrop.show')
  if ((await bootstrapBackdrop.count()) === 0) return
  await expect(bootstrapBackdrop).toBeVisible()
  await waitForValueToSettle(page, 'Backdrop opacity', async () =>
    Number(await bootstrapBackdrop.evaluate(element => getComputedStyle(element).opacity))
  )
}

async function waitForModal(page: Page, title: string | RegExp): Promise<void> {
  const content = page.locator(`${OPEN_MODAL} .modal-content`)
  await expect(content).toBeVisible()
  await expect(page.locator(`${OPEN_MODAL} .modal-title`)).toHaveText(title)
  await expect(
    page.locator(`${OPEN_MODAL} .spinner-border, ${OPEN_MODAL} .loading-spinner`)
  ).toHaveCount(0, { timeout: MODAL_CONTENT_TIMEOUT_MS })
  await Promise.all([waitForBoxHeightToSettle(page, content), waitForBackdropToSettle(page)])
}
```

Then update the assertion at what is currently line 154 in the `modal confirm` test:

```ts
await expect(page.locator(OPEN_MODAL)).toHaveCount(0)
```

**Do not touch** `page.route('**/api/portfolio-summary/recalculate**', route => route.abort())` or the cancel-button dismissal in that test.

- [ ] **Step 3: Run the gate to verify nothing changed**

```bash
npx playwright test --grep "modal"
```

Expected: same 7 passes. The helpers now tolerate both shapes; no modal has migrated yet, so behaviour is identical.

- [ ] **Step 4: Commit**

```bash
git add ui/tests/visual/states.spec.ts
git commit -m "Decouple modal visual helpers from Bootstrap JS"
```

---

## Task 3: `confirm-dialog` onto the shell

Migrated first because it is self-contained (no external driver), has the richest existing test file, and is covered by the `modal-confirm` capture.

**Files:**

- Modify: `ui/components/shared/confirm-dialog.vue`
- Test: `ui/components/shared/confirm-dialog.test.ts` (rewrite)

**Interfaces:**

- Consumes: `<modal-shell v-model:open="..." :modal-id :title :close-on-esc>` from Task 1.
- Produces: unchanged public API — props `modelValue`, `modalId`, `title`, `message`, `confirmText`, `cancelText`, `confirmClass`; emits `update:modelValue`, `confirm`, `cancel`.

- [ ] **Step 1: Rewrite the test**

Replace the entire contents of `ui/components/shared/confirm-dialog.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmDialog from './confirm-dialog.vue'

const createWrapper = (props = {}) =>
  mount(ConfirmDialog, { props: { modelValue: false, ...props }, attachTo: document.body })

describe('ConfirmDialog', () => {
  describe('visibility', () => {
    it('stays closed when modelValue is false', () => {
      const wrapper = createWrapper()

      expect(wrapper.find('dialog').element.open).toBe(false)
    })

    it('opens the dialog when modelValue becomes true', async () => {
      const wrapper = createWrapper()

      await wrapper.setProps({ modelValue: true })

      expect(wrapper.find('dialog').element.open).toBe(true)
    })

    it('closes the dialog when modelValue becomes false', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.setProps({ modelValue: false })

      expect(wrapper.find('dialog').element.open).toBe(false)
    })

    it('dont close on escape because the choice is mandatory', async () => {
      const wrapper = createWrapper({ modelValue: true })

      const event = new Event('cancel', { cancelable: true })
      wrapper.find('dialog').element.dispatchEvent(event)
      await wrapper.vm.$nextTick()

      expect(event.defaultPrevented).toBe(true)
    })
  })

  describe('content display', () => {
    it('should display default text', () => {
      const wrapper = createWrapper({ modelValue: true })

      expect(wrapper.find('.modal-title').text()).toBe('Confirm')
      expect(wrapper.find('.modal-body p').text()).toBe('Are you sure?')
      expect(wrapper.find('[data-testid="confirmDialogCancelButton"]').text()).toBe('Cancel')
      expect(wrapper.findAll('button').filter(b => b.text() === 'Confirm')).toHaveLength(1)
    })

    it('should display custom text', () => {
      const wrapper = createWrapper({
        modelValue: true,
        title: 'Kustuta üksus',
        message: 'Seda ei saa tagasi võtta.',
        confirmText: 'Kustuta',
        cancelText: 'Jäta alles',
      })

      expect(wrapper.find('.modal-title').text()).toBe('Kustuta üksus')
      expect(wrapper.find('.modal-body p').text()).toBe('Seda ei saa tagasi võtta.')
      expect(wrapper.find('[data-testid="confirmDialogCancelButton"]').text()).toBe('Jäta alles')
    })

    it('should apply custom confirm button class', () => {
      const wrapper = createWrapper({ modelValue: true, confirmClass: 'btn-danger' })

      const confirmButton = wrapper.findAll('button').filter(b => b.text() === 'Confirm')[0]

      expect(confirmButton.classes()).toContain('danger')
    })

    it('should use custom modal id', () => {
      const wrapper = createWrapper({ modelValue: true, modalId: 'deleteConfirmModal' })

      expect(wrapper.find('dialog').attributes('id')).toBe('deleteConfirmModal')
      expect(wrapper.find('dialog').attributes('aria-labelledby')).toBe('deleteConfirmModalLabel')
    })
  })

  describe('user interactions', () => {
    it('should emit confirm event when confirm button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper
        .findAll('button')
        .filter(b => b.text() === 'Confirm')[0]
        .trigger('click')

      expect(wrapper.emitted('confirm')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when cancel button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('[data-testid="confirmDialogCancelButton"]').trigger('click')

      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when close button clicked', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('.btn-close').trigger('click')

      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should emit cancel event when clicking backdrop', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('dialog').trigger('click')

      expect(wrapper.emitted('cancel')).toHaveLength(1)
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('should not emit cancel when clicking modal content', async () => {
      const wrapper = createWrapper({ modelValue: true })

      await wrapper.find('.modal-content').trigger('click')

      expect(wrapper.emitted('cancel')).toBeFalsy()
    })
  })
})
```

Note the deliberate coverage change: the old file asserted `aria-hidden="true"` and `tabindex="-1"` on a _visible_ modal. Both were Bootstrap requirements, and `aria-hidden` on a visible dialog is an accessibility defect. Native `<dialog>` handles focus and inertness itself, so those assertions are dropped rather than ported.

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx vitest run ui/components/shared/confirm-dialog.test.ts`
Expected: FAIL — `wrapper.find('dialog')` finds nothing; the component still renders `<div class="modal fade">`.

- [ ] **Step 3: Rewrite the component**

Replace the entire contents of `ui/components/shared/confirm-dialog.vue`:

```vue
<template>
  <modal-shell
    :open="modelValue"
    :modal-id="modalId"
    :title="title"
    :close-on-esc="false"
    @update:open="onDialogClosed"
  >
    <p>{{ message }}</p>
    <template #footer>
      <button
        type="button"
        class="dialog-btn"
        @click="cancel"
        data-testid="confirmDialogCancelButton"
      >
        {{ cancelText }}
      </button>
      <button
        type="button"
        class="dialog-btn"
        :class="{
          primary: confirmClass === 'btn-primary',
          danger: confirmClass === 'btn-danger',
        }"
        @click="confirm"
        data-testid="confirmDialogConfirmButton"
      >
        {{ confirmText }}
      </button>
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import ModalShell from './modal-shell.vue'

interface Props {
  modelValue: boolean
  modalId?: string
  title?: string
  message?: string
  confirmText?: string
  cancelText?: string
  confirmClass?: string
}

withDefaults(defineProps<Props>(), {
  modalId: 'confirmModal',
  title: 'Confirm',
  message: 'Are you sure?',
  confirmText: 'Confirm',
  cancelText: 'Cancel',
  confirmClass: 'btn-primary',
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
  cancel: []
}>()

const confirm = () => {
  emit('confirm')
  emit('update:modelValue', false)
}

const cancel = () => {
  emit('cancel')
  emit('update:modelValue', false)
}

const onDialogClosed = () => {
  cancel()
}
</script>
```

`onDialogClosed` fires for the close button and backdrop click, which is exactly the old `cancel()` path — the old component wired both to `cancel` too. The explicit `@click="cancel"` on the footer button emits `cancel` and closes via the parent's `modelValue` flip.

- [ ] **Step 4: Run the test to verify it passes**

Run: `npx vitest run ui/components/shared/confirm-dialog.test.ts`
Expected: PASS.

- [ ] **Step 5: Run the pixel gate for this modal**

```bash
npx playwright test --grep "modal confirm"
```

Expected: PASS with zero diff pixels. If it fails, read the diff image in `test-results/` before changing anything — the two likely causes are the missing `fade`/`show` opacity end-state and the body scroll-lock padding.

- [ ] **Step 6: Commit**

```bash
git add ui/components/shared/confirm-dialog.vue ui/components/shared/confirm-dialog.test.ts
git commit -m "Move confirm dialog to native dialog shell"
```

---

## Task 4: `instrument-modal` onto the shell

**Files:**

- Modify: `ui/components/instruments/instrument-modal.vue`
- Modify: `ui/components/instruments/instruments-view.vue:92,197-200,189`
- Test: `ui/components/instruments/instrument-modal.test.ts` (rewrite)

**Interfaces:**

- Consumes: `<modal-shell>` from Task 1.
- Produces: `instrument-modal` props become `open: boolean` (required) and `instrument?: Partial<InstrumentDto>`; emits `save: [data]` and `update:open: [value]`. The `modalId` prop is **removed** — the id is now fixed at `instrumentModal` inside the component, since it existed solely to feed `document.getElementById`.

- [ ] **Step 1: Rewrite the test**

Replace `ui/components/instruments/instrument-modal.test.ts` with a version driving the new API. Read the existing file first to preserve any assertions about `instrument-form` wiring, then adapt this skeleton:

```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InstrumentModal from './instrument-modal.vue'

const createWrapper = (props = {}) =>
  mount(InstrumentModal, {
    props: { open: false, ...props },
    global: { stubs: { InstrumentForm: true } },
    attachTo: document.body,
  })

describe('InstrumentModal', () => {
  it('stays closed when open is false', () => {
    const wrapper = createWrapper()

    expect(wrapper.find('dialog').element.open).toBe(false)
  })

  it('opens the dialog when open becomes true', async () => {
    const wrapper = createWrapper()

    await wrapper.setProps({ open: true })

    expect(wrapper.find('dialog').element.open).toBe(true)
  })

  it('shows the add title when no instrument id is present', () => {
    const wrapper = createWrapper({ open: true })

    expect(wrapper.find('.modal-title').text()).toBe('Add New Instrument')
  })

  it('shows the edit title when an instrument id is present', () => {
    const wrapper = createWrapper({ open: true, instrument: { id: 7 } })

    expect(wrapper.find('.modal-title').text()).toBe('Edit Instrument')
  })

  it('closes on escape', async () => {
    const wrapper = createWrapper({ open: true })

    const event = new Event('cancel', { cancelable: true })
    wrapper.find('dialog').element.dispatchEvent(event)
    await wrapper.vm.$nextTick()

    expect(event.defaultPrevented).toBe(false)
  })

  it('emits update:open false when the cancel button is clicked', async () => {
    const wrapper = createWrapper({ open: true })

    await wrapper
      .findAll('button')
      .filter(b => b.text() === 'Cancel')[0]
      .trigger('click')

    expect(wrapper.emitted('update:open')?.[0]).toEqual([false])
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npx vitest run ui/components/instruments/instrument-modal.test.ts`
Expected: FAIL — no `dialog` element, and `open` is not a declared prop.

- [ ] **Step 3: Rewrite the component**

Replace `ui/components/instruments/instrument-modal.vue`:

```vue
<template>
  <modal-shell
    :open="open"
    modal-id="instrumentModal"
    :title="isEditing ? 'Edit Instrument' : 'Add New Instrument'"
    @update:open="emit('update:open', $event)"
  >
    <instrument-form :initial-data="instrument" @submit="handleSave" />
    <template #footer>
      <button type="button" class="btn btn-secondary" @click="emit('update:open', false)">
        Cancel
      </button>
      <button type="submit" class="btn btn-primary" form="instrumentForm">
        {{ isEditing ? 'Update' : 'Save' }} Instrument
      </button>
    </template>
  </modal-shell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ModalShell from '../shared/modal-shell.vue'
import InstrumentForm from './instrument-form.vue'
import { InstrumentDto } from '../../models/generated/domain-models'

interface Props {
  open: boolean
  instrument?: Partial<InstrumentDto>
}

const props = withDefaults(defineProps<Props>(), {
  instrument: () => ({}),
})

const emit = defineEmits<{
  save: [data: Partial<InstrumentDto>]
  'update:open': [value: boolean]
}>()

const isEditing = computed(() => !!props.instrument?.id)

const handleSave = (data: Partial<InstrumentDto>) => {
  emit('save', data)
}
</script>
```

The two `data-bs-dismiss="modal"` attributes become the shell's own close button and an explicit `@click` on Cancel.

- [ ] **Step 4: Rewire the driver**

In `ui/components/instruments/instruments-view.vue`:

Replace line 92:

```ts
const isInstrumentModalOpen = ref(false)
```

Update the template at line 60:

```vue
<instrument-modal
  v-model:open="isInstrumentModalOpen"
  :instrument="selectedItem || {}"
  @save="onSave"
/>
```

Replace `hideModal()` at line 189 with `isInstrumentModalOpen.value = false`, and `showModal()` at line 199 with `isInstrumentModalOpen.value = true`.

Leave the two `useBootstrapModal` calls for the windows modals alone — Task 5 removes them.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
npx vitest run ui/components/instruments/
```

Expected: PASS. `instruments-view.test.ts` may reference the old wiring; if it fails, update it to the `v-model:open` API rather than reintroducing the composable.

- [ ] **Step 6: Commit**

```bash
git add ui/components/instruments/instrument-modal.vue ui/components/instruments/instrument-modal.test.ts ui/components/instruments/instruments-view.vue
git commit -m "Move instrument modal to native dialog shell"
```

---

## Task 5: Windows modals onto the shell, delete the composable

`xirr-windows-modal` and `annual-windows-modal` are structurally identical. Migrating both in one task lets `use-bootstrap-modal.ts` be deleted in the same commit, so the tree never has a half-used composable.

**Files:**

- Modify: `ui/components/instruments/xirr-windows-modal.vue`
- Modify: `ui/components/instruments/annual-windows-modal.vue`
- Modify: `ui/components/instruments/instruments-view.vue:93-96,61-65`
- Delete: `ui/composables/use-bootstrap-modal.ts`, `ui/composables/use-bootstrap-modal.test.ts`

**Interfaces:**

- Consumes: `<modal-shell>` from Task 1.
- Produces: both modals keep props `open: boolean`, `platforms?: string[]` and gain emit `update:open: [value: boolean]`. The `open` prop now drives visibility **and** the existing data load; the `watch` that calls `load()` is unchanged.

- [ ] **Step 1: Rewrite `xirr-windows-modal.vue`'s template**

Replace lines 1-59 with:

```vue
<template>
  <modal-shell
    :open="open"
    modal-id="xirrWindowsModal"
    title="Annualized return over time"
    centered
    @update:open="emit('update:open', $event)"
  >
    <div v-if="isLoading" class="text-center py-3">
      <div class="spinner-border" role="status" />
    </div>
    <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
    <div v-else>
      <table class="table table-sm mb-0">
        <thead>
          <tr>
            <th>Window</th>
            <th class="text-end">Annualized XIRR</th>
            <th class="d-none d-sm-table-cell text-end">Since</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in windows" :key="row.period">
            <td class="fw-semibold">{{ row.period }}</td>
            <td class="text-end" :class="returnClass(row.xirr)">
              {{ formatXirr(row.xirr) }}
            </td>
            <td class="d-none d-sm-table-cell text-end text-muted">
              {{ row.fromDate ?? '—' }}
            </td>
          </tr>
        </tbody>
      </table>
      <p class="text-muted small fst-italic mt-3 mb-0">
        Synthetic open at window start (portfolio value), real cash flows during the window,
        synthetic close today. Rows show "—" when the window predates your earliest portfolio
        snapshot.
      </p>
    </div>
    <template #footer>
      <button type="button" class="btn btn-secondary" @click="emit('update:open', false)">
        Close
      </button>
    </template>
  </modal-shell>
</template>
```

Add to the `<script setup>` block, after the existing `props` declaration:

```ts
const emit = defineEmits<{
  'update:open': [value: boolean]
}>()
```

and add the import alongside the existing ones:

```ts
import ModalShell from '../shared/modal-shell.vue'
```

Leave `platformsKey`, `load`, the `watch`, `formatXirr`, and `returnClass` untouched.

- [ ] **Step 2: Apply the identical change to `annual-windows-modal.vue`**

Same edit with these substitutions: `modal-id="annualWindowsModal"`, `title="Buy-and-hold annualized return"`, header cell `Annualized return`, `returnClass(row.annualReturn)`, `formatReturn(row.annualReturn)`, and the existing buy-and-hold paragraph text preserved verbatim:

```
Synthetic buy-and-hold using current shares × historical close price at window start
vs current value. Real transactions during the window are ignored. "Since" clamps to
the earliest available price when history is shorter than the window.
```

- [ ] **Step 3: Rewire the driver and delete the composable**

In `ui/components/instruments/instruments-view.vue`, replace lines 93-96 with:

```ts
const isXirrWindowsModalOpen = ref(false)
const isAnnualWindowsModalOpen = ref(false)
```

Update the template at lines 61-65:

```vue
<xirr-windows-modal v-model:open="isXirrWindowsModalOpen" :platforms="effectivePlatformsForXirr" />
<annual-windows-modal
  v-model:open="isAnnualWindowsModalOpen"
  :platforms="effectivePlatformsForXirr"
/>
```

The `instrument-table` emits `@show-xirr-windows` and `@show-annual-windows`, currently bound to `showXirrWindowsModal` / `showAnnualWindowsModal` at lines 54-55. Rebind them:

```vue
@show-xirr-windows="isXirrWindowsModalOpen = true" @show-annual-windows="isAnnualWindowsModalOpen =
true"
```

Remove the `useBootstrapModal` import at line 75, then delete both files:

```bash
git rm ui/composables/use-bootstrap-modal.ts ui/composables/use-bootstrap-modal.test.ts
```

- [ ] **Step 4: Verify no references survive**

```bash
grep -rn "useBootstrapModal\|use-bootstrap-modal" ui/
```

Expected: no output.

- [ ] **Step 5: Run the tests**

```bash
npx vitest run ui/components/instruments/
```

Expected: PASS.

- [ ] **Step 6: Run the pixel gate for both windows modals**

```bash
npx playwright test --grep "modal xirr-windows|modal annual-windows|modal instrument"
```

Expected: 3 tests PASS with zero diff pixels.

- [ ] **Step 7: Commit**

```bash
git add -A ui/components/instruments ui/composables
git commit -m "Move windows modals to native dialog shell"
```

---

## Task 6: `logo-replacement-modal` onto the shell

**Files:**

- Modify: `ui/components/etf/logo-replacement-modal.vue`

**Interfaces:**

- Consumes: `<modal-shell>` from Task 1, with `size="lg"`, `:close-on-esc="false"`, and `body-class="modal-body-scroll"`.
- Produces: unchanged public API — props `modelValue`, `holdingUuid`, `holdingName`; emits `update:modelValue`, `replaced`. The `modalId` prop is **removed**; the id is fixed at `logoReplacementModal`.

- [ ] **Step 1: Replace the template**

Replace lines 1-63 of `ui/components/etf/logo-replacement-modal.vue`:

```vue
<template>
  <modal-shell
    :open="modelValue"
    modal-id="logoReplacementModal"
    :title="`Replace Logo: ${holdingName}`"
    size="lg"
    :close-on-esc="false"
    body-class="modal-body-scroll"
    @update:open="close"
  >
    <loading-spinner v-if="isLoading" class="my-4" />
    <div v-else-if="error" class="alert alert-danger">{{ error }}</div>
    <div v-else-if="hasFetched && candidates.length === 0" class="text-center text-muted py-4">
      No logo candidates found
    </div>
    <div v-else class="logo-grid">
      <div
        v-for="candidate in candidates"
        :key="candidate.index"
        class="logo-candidate"
        :class="{ selected: selectedIndex === candidate.index }"
        @click="selectCandidate(candidate.index)"
      >
        <img
          :src="candidate.imageDataUrl || candidate.thumbnailUrl"
          :alt="candidate.title"
          class="candidate-image"
          @error="handleImageError"
        />
      </div>
    </div>
    <template #footer>
      <div v-if="!holdingUuid" class="text-muted small me-auto">
        Logo preview only - no holding record to save to
      </div>
      <button type="button" class="btn btn-secondary" @click="close" :disabled="isReplacing">
        {{ holdingUuid ? 'Cancel' : 'Close' }}
      </button>
      <button
        v-if="holdingUuid"
        type="button"
        class="btn btn-primary"
        @click="confirmReplacement"
        :disabled="selectedIndex === null || isReplacing"
      >
        <span v-if="isReplacing" class="btn-spinner me-1"></span>
        {{ isReplacing ? 'Replacing...' : 'Use This Logo' }}
      </button>
    </template>
  </modal-shell>
</template>
```

- [ ] **Step 2: Update the script**

In the `<script setup>` block: delete the `import { Modal } from 'bootstrap'` line, the `modalInstance` variable, the whole `onMounted` block, and the whole `onUnmounted` block. Change the Vue import to `import { ref, watch } from 'vue'`. Add `import ModalShell from '../shared/modal-shell.vue'`.

Remove `modalId` from `Props` and drop the now-empty `withDefaults` wrapper:

```ts
interface Props {
  modelValue: boolean
  holdingUuid: string | null
  holdingName: string
}

const props = defineProps<Props>()
```

Simplify the `watch` — visibility is the shell's job now, so it only drives data:

```ts
watch(
  () => props.modelValue,
  async newValue => {
    if (newValue && (props.holdingUuid || props.holdingName)) {
      await loadCandidates()
      return
    }
    resetState()
  }
)
```

Leave `close`, `loadCandidates`, `selectCandidate`, `confirmReplacement`, `resetState`, and `handleImageError` unchanged.

- [ ] **Step 3: Move the body scroll style out of the removed wrapper**

The scoped `.modal-body-scroll` rule (lines 188-191) still applies — it is passed through as `body-class` and lands on the shell's `.modal-body`. Because it is now on a child component's element, the scoped attribute will not match. Change the selector to use `:deep()`:

```css
:deep(.modal-body-scroll) {
  max-height: 60vh;
  overflow-y: auto;
}
```

Leave `.logo-grid`, `.logo-candidate`, `.candidate-image` unchanged — those elements are in this component's own slot content, so scoping still works.

- [ ] **Step 4: Run the tests**

```bash
npx vitest run ui/components/etf/
```

Expected: PASS.

- [ ] **Step 5: Run the pixel gate**

```bash
npx playwright test --grep "modal logo-replacement"
```

Expected: PASS with zero diff pixels. `max-height: 60vh` on the body is the highest-risk rule in this task — if the diff shows a taller or shorter body, the `:deep()` selector did not take effect.

- [ ] **Step 6: Commit**

```bash
git add ui/components/etf/logo-replacement-modal.vue
git commit -m "Move logo replacement modal to dialog shell"
```

---

## Task 7: `config-dialog` onto the shell

**Files:**

- Modify: `ui/components/diversification/config-dialog.vue`
- Test: `ui/components/diversification/config-dialog.test.ts` (rewrite)

**Interfaces:**

- Consumes: `<modal-shell>` from Task 1, with `size="lg"` and `:close-on-esc="false"`.
- Produces: unchanged public API — props `modelValue`, `mode`, `config`, `validEtfIds`; emits `update:modelValue`, `export`, `import`. The `modalId` prop is **removed**; the id is fixed at `configDialog`.

- [ ] **Step 1: Read the existing test and note which behaviours are Bootstrap-coupled**

```bash
cat ui/components/diversification/config-dialog.test.ts
```

Everything asserting on `Modal` mock calls, `.modal.fade`, `aria-hidden`, or `tabindex` gets dropped. Everything asserting on file parsing, validation warnings, the Monaco editor content, and the export/import emits is behaviour that must survive untouched.

- [ ] **Step 2: Rewrite the visibility assertions in the test**

Replace any Bootstrap-mock test with the native equivalents, keeping the rest of the file as-is:

```ts
it('opens the dialog when modelValue becomes true', async () => {
  const wrapper = createWrapper()

  await wrapper.setProps({ modelValue: true })

  expect(wrapper.find('dialog').element.open).toBe(true)
})

it('closes the dialog when modelValue becomes false', async () => {
  const wrapper = createWrapper({ modelValue: true })

  await wrapper.setProps({ modelValue: false })

  expect(wrapper.find('dialog').element.open).toBe(false)
})

it('dont close on escape', async () => {
  const wrapper = createWrapper({ modelValue: true })

  const event = new Event('cancel', { cancelable: true })
  wrapper.find('dialog').element.dispatchEvent(event)
  await wrapper.vm.$nextTick()

  expect(event.defaultPrevented).toBe(true)
})
```

Delete the `vi.mock('bootstrap', ...)` block and the `Modal` import if present.

- [ ] **Step 3: Run the test to verify it fails**

Run: `npx vitest run ui/components/diversification/config-dialog.test.ts`
Expected: FAIL — no `dialog` element.

- [ ] **Step 4: Replace the template**

Replace lines 1-99 of `ui/components/diversification/config-dialog.vue`:

```vue
<template>
  <modal-shell
    :open="modelValue"
    modal-id="configDialog"
    :title="mode === 'export' ? 'Export Configuration' : 'Import Configuration'"
    size="lg"
    :close-on-esc="false"
    @update:open="onDialogClosed"
  >
    <template v-if="mode === 'export'">
      <p class="text-muted small mb-2">
        Download your current ETF allocation configuration as a JSON file.
      </p>
      <div class="editor-container">
        <VueMonacoEditor
          v-model:value="exportContent"
          language="json"
          :options="editorOptions"
          theme="vs"
        />
      </div>
    </template>
    <template v-else>
      <div v-if="!importedData" class="import-area">
        <p class="text-muted small mb-3">
          Select a JSON configuration file to import your ETF allocation.
        </p>
        <div
          class="file-drop-zone"
          @click="triggerFileInput"
          @dragover.prevent
          @drop.prevent="onFileDrop"
        >
          <input
            ref="fileInput"
            type="file"
            accept=".json"
            class="d-none"
            @change="onFileSelected"
          />
          <div class="drop-content">
            <div class="drop-icon">+</div>
            <div>Click to select or drag a JSON file here</div>
          </div>
        </div>
        <div v-if="importError" class="alert alert-danger mt-3 mb-0">
          {{ importError }}
        </div>
      </div>
      <div v-else class="import-preview">
        <p class="text-muted small mb-2">Preview of configuration to import:</p>
        <div class="editor-container">
          <VueMonacoEditor
            v-model:value="importContent"
            language="json"
            :options="editorOptions"
            theme="vs"
          />
        </div>
        <div v-if="validationWarning" class="alert alert-warning mt-3 mb-0">
          {{ validationWarning }}
        </div>
      </div>
    </template>
    <template #footer>
      <button type="button" class="dialog-btn" @click="close">Cancel</button>
      <template v-if="mode === 'export'">
        <button type="button" class="dialog-btn primary" @click="downloadConfig">Download</button>
      </template>
      <template v-else>
        <button v-if="importedData" type="button" class="dialog-btn" @click="resetImport">
          Choose Different File
        </button>
        <button
          type="button"
          class="dialog-btn primary"
          :disabled="!importedData"
          @click="confirmImport"
        >
          Import
        </button>
      </template>
    </template>
  </modal-shell>
</template>
```

- [ ] **Step 5: Update the script**

Delete `import { Modal } from 'bootstrap'`, the `modalInstance` variable, the `onMounted` block, the `onUnmounted` block, and the `watch` on `modelValue` (the shell owns visibility now). Change the Vue import to `import { ref, computed } from 'vue'`. Add `import ModalShell from '../shared/modal-shell.vue'`.

Remove `modalId` from `Props` and drop `withDefaults`:

```ts
const props = defineProps<Props>()
```

The old `hidden.bs.modal` listener did two things — emit `false` and `resetImport()`. Preserve both:

```ts
const onDialogClosed = () => {
  emit('update:modelValue', false)
  resetImport()
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
npx vitest run ui/components/diversification/
```

Expected: PASS.

- [ ] **Step 7: Run the pixel gate for both config captures**

```bash
npx playwright test --grep "modal config-export|modal config-import"
```

Expected: 2 tests PASS with zero diff pixels. Monaco renders asynchronously inside the screenshot — if a diff appears in the editor gutter, check for the known active-indent-guide flake before assuming this task caused it.

- [ ] **Step 8: Commit**

```bash
git add ui/components/diversification/config-dialog.vue ui/components/diversification/config-dialog.test.ts
git commit -m "Move config dialog to native dialog shell"
```

---

## Task 8: Behaviour coverage the pixel gate cannot provide

Issue #1671 calls this out explicitly: a dismiss control whose handler is gone renders identically to a working one. All six modals could be undismissable and every screenshot would still be green.

**Files:**

- Modify: `ui/tests/visual/states.spec.ts`

**Interfaces:**

- Consumes: every migrated modal from Tasks 3-7.
- Produces: a `modal dismissal` describe block asserting close behaviour per modal.

- [ ] **Step 1: Add the dismissal test block**

Append inside the existing `test.describe('modals', ...)` block in `ui/tests/visual/states.spec.ts`, after the screenshot loop:

```ts
for (const modal of MODALS) {
  test(`modal ${modal.name} dismisses`, async ({ page }) => {
    await modal.stub(page)
    await openRoute(page, modal.route)
    await modal.open(page)
    await waitForModal(page, modal.title)

    await page.locator(`${OPEN_MODAL} .btn-close`).click()

    await expect(page.locator(OPEN_MODAL)).toHaveCount(0)
  })
}

test('modal escape closes a dismissable modal', async ({ page }) => {
  await stubInstrumentsWithWindows(page)
  await openRoute(page, '/instruments')
  await visibleTotalsTriggers(page).nth(0).click()
  await waitForModal(page, 'Annualized return over time')

  await page.keyboard.press('Escape')

  await expect(page.locator(OPEN_MODAL)).toHaveCount(0)
})

test('modal escape dont close the confirm dialog', async ({ page }) => {
  await stubPortfolioSummary(page)
  await page.route('**/api/portfolio-summary/recalculate**', route => route.abort())
  await openRoute(page, '/')
  await page.click('button:has-text("Recalculate Data")')
  await waitForModal(page, 'Recalculate Portfolio Data')

  await page.keyboard.press('Escape')

  await expect(page.locator(OPEN_MODAL)).toHaveCount(1)
  await page.click('[data-testid="confirmDialogCancelButton"]')
})
```

The last test asserts the deliberately preserved `keyboard: false` behaviour from the inventory table. It ends by cancelling so the destructive confirm is never reachable.

- [ ] **Step 2: Run the new tests**

```bash
npx playwright test --grep "dismisses|escape"
```

Expected: all PASS. A failure here is the real payload of this task — it means a dismiss path is dead, which no screenshot would have caught.

- [ ] **Step 3: Commit**

```bash
git add ui/tests/visual/states.spec.ts
git commit -m "Cover modal dismissal paths"
```

---

## Task 9: Tighten the harness and close out the phase

**Files:**

- Modify: `ui/tests/visual/states.spec.ts:62-72` and the `OPEN_MODAL` constant

- [ ] **Step 1: Replace the Bootstrap data-API trigger**

`openInstrumentModal` currently fabricates a `data-bs-toggle` element. Nothing in the app can open this modal any more — `instruments-view` passes `:show-add-button="false"` to `crud-layout`, so the real trigger is never rendered. Replace the synthetic Bootstrap click with a synthetic native one:

```ts
async function openInstrumentModal(page: Page): Promise<void> {
  await page.evaluate(() => {
    document.querySelector<HTMLDialogElement>('#instrumentModal')?.showModal()
  })
}
```

This is no more synthetic than what it replaces, and it removes the last Bootstrap-JS dependency from the harness. Note in the PR that the instrument modal has no reachable UI trigger — a pre-existing oddity this phase does not fix.

- [ ] **Step 2: Narrow the selector now that every modal is native**

```ts
const OPEN_MODAL = 'dialog.modal[open]'
```

- [ ] **Step 3: Delete the now-dead Bootstrap backdrop branch**

`waitForBackdropToSettle` can never find `.modal-backdrop.show` any more. Replace the whole helper with a dialog-opacity settle, and update its call site in `waitForModal`:

```ts
async function waitForBackdropToSettle(page: Page): Promise<void> {
  const dialog = page.locator(OPEN_MODAL)
  await waitForValueToSettle(page, 'Dialog opacity', async () =>
    Number(await dialog.evaluate(element => getComputedStyle(element).opacity))
  )
}
```

- [ ] **Step 4: Verify every exit criterion from issue #1671**

```bash
grep -rn "from 'bootstrap'" ui/          # expect only use-toast.ts and transactions-view.vue (Phase 6)
grep -rn 'data-bs-dismiss' ui/           # expect no output
grep -rn 'useBootstrapModal' ui/         # expect no output
grep -rn 'modalId' ui/components/instruments/instrument-modal.vue   # expect no output
```

- [ ] **Step 5: Run everything**

```bash
npm test -- --run
npm run lint-format
git checkout ui/models/generated/domain-models.ts
npx playwright test
```

Expected: unit suite green; lint-format exit 0; visual suite green except the known pre-existing `state loading skeleton` desktop failure.

- [ ] **Step 6: Commit and open the PR**

```bash
git add ui/tests/visual/states.spec.ts
git commit -m "Drop Bootstrap JS from the modal harness"
git push -u origin feature/1671-tailwind-phase-5-modals
```

PR body:

```markdown
## Summary

- Replace Bootstrap `Modal` JS with a single native `<dialog>` shell driven by `v-model:open`
- Migrate all six modals onto the shell; delete `use-bootstrap-modal`
- Decouple the visual harness from `.modal.show`, `.modal-backdrop`, and the `data-bs-toggle` data-API
- Add dismissal and Esc coverage, which the pixel gate structurally cannot provide

## Test plan

- [ ] `npm test -- --run`
- [ ] `npm run lint-format`
- [ ] `npx playwright test` — modal captures diff = 0
- [ ] Modal dismissal and Esc behaviour covered by new tests

Closes #1671
Refs #1662
```

---

## Self-Review

**Spec coverage.** Every issue #1671 exit criterion maps to a task: shell created (1), six modals migrated (3-7), `use-bootstrap-modal` deleted (5), no `data-bs-dismiss` (4, 5, 9 verify), `modalId` prop gone (4, 9 verify), four coupled tests rewritten (1, 3, 4, 7 — `use-bootstrap-modal.test.ts` deleted in 5 and replaced by `modal-shell.test.ts` in 1), click-through coverage (8), diff = 0 (per-task gates plus 9).

**Two gaps found while planning, now covered.** The visual harness's own Bootstrap coupling (Tasks 2 and 9) and the body scroll-lock padding compensation (Task 1) were not in the issue. Both would have surfaced as confusing red gates mid-implementation.

**Type consistency.** The shell's contract — props `open`/`modalId`/`title`/`size`/`centered`/`closeOnEsc`/`bodyClass`, emit `update:open` — is used identically in Tasks 3-7. Consumer-facing APIs are unchanged except for the two deliberate removals (`modalId` on `instrument-modal`, `logo-replacement-modal`, and `config-dialog`) and the deliberate addition (`open` + `update:open` on `instrument-modal`, `xirr-windows-modal`, `annual-windows-modal`).

**Deliberate behaviour changes, all noted in-place.** `aria-hidden="true"` and `tabindex="-1"` are dropped (native `<dialog>` supersedes both, and `aria-hidden` on a visible modal was a defect). Everything else — including the non-obvious `keyboard: false` on three modals — is preserved exactly and asserted in Task 8.
