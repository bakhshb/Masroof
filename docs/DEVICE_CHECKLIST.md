# Physical-device validation checklist

Headless CI cannot verify these. Run on a physical Android phone before release.

## Permissions and privacy

- [ ] First-launch SMS privacy explanation is clear (Arabic)
- [ ] READ_SMS grant/deny/permanent-deny paths work
- [ ] RECEIVE_SMS grant for auto-import settings works
- [ ] App never requests SEND_SMS
- [ ] Android backup remains disabled

## Journeys

- [ ] Onboarding: tracking start + opening balance + add accounts + optional SMS bind + import range → Home
- [ ] Link account from real bank SMS («ربط الحساب برسالة بنكية»)
- [ ] Same sender with multiple accounts + one credit card matches correctly / reviews when ambiguous
- [ ] Import date range + registered-only filtering
- [ ] Discovery mode does not post unregistered senders into balances
- [ ] Review queue: link, remember rule, ignore, re-analyze; counts update immediately
- [ ] Two-sided review: card payment requires bank + card; internal transfer requires both owned accounts
- [ ] Near-duplicate SMS lands in review as «محتمل تكرار» and does not auto-post
- [ ] Home attention banners open Review; empty-state opens Import
- [ ] Bottom nav returns correctly from Import/Review child routes
- [ ] Credit-card purchase increases liability once; later payment is not a second expense
- [ ] Internal transfer does not distort income/expenses
- [ ] Font scale / long Arabic merchant names do not clip

## Appearance and dashboard

- [ ] Settings → المظهر: System / Light / Dark apply immediately (no restart)
- [ ] Dark mode: hero card, badges, attention banners, and charts remain readable
- [ ] Home donut shows month composition when data exists; hides when empty
- [ ] Home daily expense column chart renders for the selected month
- [ ] Financial history liquidity column chart renders for the selected month
- [ ] Bottom nav uses filled icons when selected, outlined when not
- [ ] More menu rows show leading icons

## Release

- [ ] Install debug APK from `assembleDebug` (`masroof-debug.apk`)
- [ ] Spot-check Diagnostics sanitization (no raw SMS/OTP dump)
