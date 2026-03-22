---
name: dv-register-report-launcher
overview: Add a Disbursement Voucher register report launcher patterned after the existing Journal Voucher Register, using DVRegister.jrxml while keeping similar report parameters and security.
todos:
  - id: create-dv-register-viewmodel
    content: Create DVRegisterViewModel based on JournalVoucherRegisterViewModel and point it to DVRegister.jasper with appropriate parameters.
    status: completed
  - id: add-dv-register-zul
    content: Create dv_register.zul patterned after journal_voucher_register.zul and bind it to DVRegisterViewModel.
    status: completed
  - id: wire-dv-menu-and-click-handler
    content: Add Disbursement Voucher Register menu entry to main.zul and onClick handler in BorderLayoutComposer.
    status: completed
  - id: extend-systemaccessenum-for-dv-register
    content: Add new SystemAccessEnum entry mnuDVRegister and hook it into security/seed data.
    status: completed
  - id: compile-and-test-dv-report-launcher
    content: Ensure DVRegister.jrxml is compiled and test PDF/XLS export flows and permissions.
    status: completed
isProject: false
---

### Goal

Create a new UI entry point and backing ViewModel that launches the `DVRegister.jrxml` Jasper report, mirroring the behavior and parameters of the existing Journal Voucher Register launcher.

### Key existing references

- **JV report launcher UI**: `[loans-manager/src/main/webapp/WEB-INF/gl/journal_voucher_register.zul](loans-manager/src/main/webapp/WEB-INF/gl/journal_voucher_register.zul)`
- **JV ViewModel**: `[loans-manager/src/main/java/com/belcomsoft/gl/JournalVoucherRegisterViewModel.java](loans-manager/src/main/java/com/belcomsoft/gl/JournalVoucherRegisterViewModel.java)`
- **Main menu & access control**: `[loans-manager/src/main/webapp/WEB-INF/main.zul](loans-manager/src/main/webapp/WEB-INF/main.zul)`, `[loans-manager/src/main/java/com/belcomsoft/pm/ui/main/BorderLayoutComposer.java](loans-manager/src/main/java/com/belcomsoft/pm/ui/main/BorderLayoutComposer.java)`, `[loans-model/src/main/java/com/lfs/domain/keyword/SystemAccessEnum.java](loans-model/src/main/java/com/lfs/domain/keyword/SystemAccessEnum.java)`
- **New Jasper report**: `[loans-manager/src/main/webapp/WEB-INF/jasper/DVRegister.jrxml](loans-manager/src/main/webapp/WEB-INF/jasper/DVRegister.jrxml)`

### Implementation plan

- **1. Define DV register ViewModel (Java)**
  - Create `com.belcomsoft.gl.DVRegisterViewModel` modeled closely on `JournalVoucherRegisterViewModel`.
  - Reuse the same user context and company wiring (`getUser()`, `MfCompany`, `coId`, `busunitId`, `userName`).
  - Adjust the report file handling to use `DVRegister.jasper` instead of `JournalVoucherGrouped.jasper` for both PDF and Excel exports.
  - Implement `createReportParam()` to accept the "same report parm as JV Register" requirement while matching `DVRegister.jrxml`:
    - Map `companyName`, `companyAddress`, `userName`, `coId`, `busunitId` as in the JV ViewModel.
    - For date range, translate the JV-style period range parameters (fiscal year + `fromPeriod`/`toPeriod`) into concrete `fromDate`/`toDate` values that match the `DVRegister` query parameters; use existing GL period/calendar services if available or add a dedicated method to resolve period IDs to dates.
    - Ensure the parameter names match exactly those in `DVRegister.jrxml` (`fromDate`, `toDate`).
  - Keep the same toolbar contract (`exportPDF`, `exportExcel`, etc.) so it plugs into `ToolBarMvvmController` like the JV ViewModel.
- **2. Create DV register ZUL view**
  - Add a new file `[loans-manager/src/main/webapp/WEB-INF/gl/dv_register.zul](loans-manager/src/main/webapp/WEB-INF/gl/dv_register.zul)` patterned after `journal_voucher_register.zul`.
  - Bind `viewModel` to `com.belcomsoft.gl.DVRegisterViewModel` and wire the same toolbar buttons: Print to PDF, Export to XLS, Close.
  - Reuse the JV input controls for parameters (fiscal year, from period, to period, and possibly status if you want identical filters), but adapt labels and caption to “Disbursement Voucher Register”.
  - Include an `iframe` for inline PDF viewing, matching the JV implementation.
- **3. Wire the menu and navigation**
  - In `main.zul`, add a new tree item under the GL **Reports** node, e.g. label "Disbursement Voucher Register", guarded by a new `hasMenuAccess` check such as `hasMenuAccess('SYSTEM_CODE_GL','mnuDVRegister','VIEW')`.
  - In `BorderLayoutComposer`, add an `onClick$treeDVRegister` handler that calls `UiUtil.displayToTab(...)` to open `/WEB-INF/gl/dv_register.zul` in a new tab, following the pattern used for `treeJVRegister`.
- **4. Define access control keys**
  - Extend `SystemAccessEnum` with `mnuDVRegister("DISBURSEMENT-VOUCHER-REGISTER")` or a similar description value.
  - Ensure your security configuration/data (e.g., SQL seed scripts or admin UI) includes the new `mnuDVRegister` entry and assigns it to the appropriate roles, mirroring `mnuJVRegister`.
- **5. Compile and register the Jasper report**
  - Ensure `DVRegister.jrxml` is compiled to `DVRegister.jasper` as part of your build/deploy process, similar to `JournalVoucherGrouped.jrxml`.
  - If you maintain `.gitignore` or resource settings for Jasper files (as seen for `JournalVoucherGrouped.jasper`), add the corresponding entries for `DVRegister.jasper` if needed.
- **6. Test the DV register launcher**
  - Log in as a user with the new `mnuDVRegister` VIEW permission and confirm the new menu item appears.
  - Open the DV Register screen, choose a fiscal year and period range that maps to a date range with data, and:
    - Run **Print to PDF** and verify the `Disbursement Voucher Register` report appears correctly with the chosen date range and company/user details.
    - Run **Export to XLS** and verify the Excel file contains the same data and respects the date range.
  - Test behavior when the user lacks the permission (menu item hidden) and when the report returns no data (appropriate message or empty report).

