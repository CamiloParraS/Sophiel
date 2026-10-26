# 08: Write M9 into SPEC and DECISIONS

Type: task
Status: resolved
Blocked by: 01, 02, 03, 04, 05, 06, 07, 09

## Question

Write SPEC **M9 — Remote** (deliverables in build order, V-items), amend C1, §1.2/§1.3, §7, §8 (server commands, flavors) and M7's checks; record each ticket's D-entry. Destination reached when a build agent needs no further decision. Human reviews.

## Answer

Done 2026-10-21; awaiting the human's review of the diff.

- **SPEC:** new §5 **M9 — Remote** (six deliverables in build order: flavors, relay, envelope, Child phone, Parent phone, deploy; V1-V9; mailbox stretch). Amended: header, C1, C5, C6, §1.1 item 6, §1.2 Remote bullet, §1.3 rows (the old "accounts, backend" row replaced by narrower ones), §2 flavors + `RELAY_URL`, §3.1 `server/`, §3.2 layout, §4.1 flavor manifests, §5 order, M7 deliverables and V1-V4, §7 item 26 reworded + the privacy claim + items 27-32, §8 commands, §9 checklist.
- **DECISIONS:** D77 (hosting), D78 (reachability), D79 (protocol), D80 (QR), D81 (Parent screens), D82 (Child side), D83 (flavors, claim), D84 (relay ops), D85 (this write-up: freeze lifted for M9 only; the endpoint list, frame shapes, QR text and token sizes in SPEC are implementation choices, free to change).
- **CLAUDE.md:** hard rules 1, 5, 6 and the verify line, worded to hold before and after M9's flavors exist.
- **For review, choices made while writing that no ticket decided:** the freeze lift (D85, implied by D76); the relay interface table and HTTP codes (401/404/410/413/503/504); QR text `sophiel:1:<claimCode>:<key>`; 256-bit tokens with SHA-256 stored; V4/V6 test shapes (A as Child, B as Parent; 30 min screen-off, `force-idle`).
