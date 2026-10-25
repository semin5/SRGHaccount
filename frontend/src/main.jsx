import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { Eye, Pencil, Plus, Save, Search, Trash2, X } from "lucide-react";
import "./style.css";

const API = "/account/api";
const TABS = [["google", "구글 계정"], ["polaris", "폴라리스"], ["nas", "NAS"],
  ["corporate-phones", "법인폰"], ["certificates", "공동인증서"], ["physicians", "진료의 계정일자"],
  ["site-accounts", "사이트 계정"]];
const COLUMNS = {
  google: [["number", "번호"], ["account", "계정"], ["password", "비밀번호"], ["department", "사용부서"], ["purpose", "용도"], ["hancom_docs_quantity", "한컴독스 수량"], ["user_1", "사용자 1"], ["user_2", "사용자 2"], ["user_3", "사용자 3"], ["user_4", "사용자 4"], ["user_5", "사용자 5"]],
  polaris: [["pc", "PC"], ["department", "부서"], ["name", "이름"], ["building", "건물"], ["floor", "층"], ["computer_name", "컴퓨터 이름"], ["license", "라이센스"], ["purchase_date", "구매일시"], ["ip", "IP"], ["installation_date", "설치일"], ["remarks", "비고"]],
  nas: [["department", "부서"], ["folder_name", "폴더명"], ["password", "비밀번호"]],
  "site-accounts": [["name", "명칭"], ["site", "사이트"], ["remarks", "비고"]],
  "corporate-phones": [["organization_level_1", "조직명 1"], ["organization_level_2", "구분"],
    ["organization_level_3", "부서"], ["organization_level_4", "세부 부서"],
    ["mobile_number", "휴대전화번호"], ["name", "이름"], ["position", "직급"], ["email", "이메일"],
    ["landline_number", "유선번호"], ["extension_number", "내선번호"], ["previous_carrier", "기존통신사"],
    ["replacement_or_new", "교체/신규"], ["identity_service_password", "본인확인서비스 비밀번호"],
    ["google_account_email", "구글 계정 이메일"], ["google_account_password", "구글 계정 비밀번호"],
    ["office_mobile_password", "오피스 모바일 앱 비밀번호"],
    ["additional_password", "비밀번호"], ["activation_status", "개통"]],
  certificates: [["task_name", "업무명칭"], ["site", "사이트"], ["ip", "IP"], ["account_id", "ID"], ["password", "PW"]],
  physicians: [["userpidn", "ID"], ["username", "이름"], ["userdept", "진료과"],
    ["userfrdt", "시작일"], ["usertodt", "종료일"], ["usersbdp", "과"],
    ["userrsdt", "시작일"], ["userpwdt", "변경예정일"], ["userdoct", "의사코드"],
    ["userdpds", "약어"], ["userdtid", "의사면허"], ["userspls", "전문의번호"], ["usercomt", "비고"]],
};
const VISIBLE = {
  google: [["account", "계정"], ["department", "사용부서"], ["user_1", "사용자 1"], ["user_2", "사용자 2"], ["user_3", "사용자 3"], ["user_4", "사용자 4"], ["user_5", "사용자 5"]],
  polaris: [["department", "부서"], ["name", "이름"], ["building", "건물"], ["floor", "층"], ["ip", "IP"], ["remarks", "비고"]],
  nas: [["department", "부서"], ["folder_name", "폴더명"]],
  "site-accounts": [["name", "명칭"], ["site", "사이트"], ["remarks", "비고"]],
  "corporate-phones": [["organization_level_2", "구분"], ["organization_level_3", "부서"],
    ["organization_level_4", "세부 부서"], ["mobile_number", "휴대전화번호"], ["name", "이름"],
    ["email", "이메일"], ["extension_number", "내선번호"], ["google_account_email", "구글 계정 이메일"]],
  certificates: [["task_name", "업무명칭"], ["site", "사이트"], ["ip", "IP"], ["account_id", "ID"]],
  physicians: [["userpidn", "ID"], ["username", "이름"], ["userdept", "진료과"],
    ["userfrdt", "시작일"], ["userpwdt", "변경예정일"], ["userdoct", "의사코드"],
    ["userdpds", "약어"], ["userdtid", "의사면허"], ["userspls", "전문의번호"]],
};
const SENSITIVE = new Set(["password", "license", "identity_service_password", "google_account_password",
  "office_mobile_password", "additional_password"]);
const PHONE_ACCOUNT_FIELDS = [["mobile_number", "휴대전화번호"], ["email", "이메일"],
  ["additional_password", "비밀번호"]];
const NUMERIC = new Set(["number", "hancom_docs_quantity", "floor"]);
const display = (value) => String(value ?? "").trim() || "—";

function useEscape(onClose) {
  useEffect(() => {
    const handler = (event) => { if (event.key === "Escape") onClose(); };
    addEventListener("keydown", handler);
    return () => removeEventListener("keydown", handler);
  }, [onClose]);
}

function EntryModal({ tab, row, lockedLicense, saving, onClose, onSubmit }) {
  useEscape(onClose);
  const [accounts, setAccounts] = useState(() => row?.accounts?.map((entry) => ({ ...entry })) ?? []);
  const [accountError, setAccountError] = useState("");
  const changeAccount = (index, key, value) => setAccounts((current) => current.map((entry, at) =>
    at === index ? { ...entry, [key]: value } : entry));
  const submit = (event) => {
    event.preventDefault();
    if (tab === "site-accounts" && accounts.some((entry) =>
      !entry.account?.trim() && !entry.password?.trim() && !entry.content?.trim())) {
      setAccountError("각 항목에 계정·암호·내용 중 하나 이상을 입력해 주세요.");
      return;
    }
    setAccountError("");
    onSubmit({ ...Object.fromEntries(new FormData(event.currentTarget).entries()),
      ...(tab === "site-accounts" ? { accounts } : {}) });
  };
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <form className="modal entry-modal" onSubmit={submit} autoComplete="off">
      <div className="modal-header"><h2>{row ? "수정" : "추가"}</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <div className="entry-grid">{COLUMNS[tab].map(([key, label]) =>
        <label key={key}>{label}
          <input name={key} type={NUMERIC.has(key) ? "number" : "text"}
            defaultValue={row?.[key] ?? (key === "license" ? lockedLicense : "") ?? ""}
            readOnly={key === "license" && lockedLicense !== null}
            autoFocus={key === COLUMNS[tab][0][0]}
            autoComplete={SENSITIVE.has(key) ? "new-password" : "off"} />
        </label>)}
        {tab === "site-accounts" && <div className="account-section">
          <div className="account-section-heading"><strong>계정 정보</strong><button type="button" className="account-add" onClick={() => setAccounts((current) => [...current, { label: "", account: "", password: "", content: "" }])}><Plus size={15} />정보 추가</button></div>
          {accounts.map((entry, index) => <div className="account-entry" key={index}>
            <label className="wide">명칭<input required value={entry.label} onChange={(event) => changeAccount(index, "label", event.target.value)} placeholder="예: 도메인, 웹 호스팅, FTP, 보안인증서" /></label>
            <label>계정 (선택)<input value={entry.account ?? ""} onChange={(event) => changeAccount(index, "account", event.target.value)} autoComplete="off" /></label>
            <label>암호 (선택)<input value={entry.password ?? ""} onChange={(event) => changeAccount(index, "password", event.target.value)} autoComplete="new-password" /></label>
            <button type="button" className="account-remove" onClick={() => setAccounts((current) => current.filter((_, at) => at !== index))} aria-label={`${index + 1}번 계정 삭제`}><Trash2 size={15} />삭제</button>
            <label className="wide">내용 (선택)<textarea value={entry.content ?? ""} onChange={(event) => changeAccount(index, "content", event.target.value)} placeholder="예: saranghp.co.kr · 암호는 위 암호 칸에 입력" /></label>
          </div>)}
          {accountError && <p className="account-error" role="alert">{accountError}</p>}
        </div>}</div>
      <div className="modal-footer"><button type="button" className="cancel" onClick={onClose}>취소</button>
        <button type="submit" className="save" disabled={saving}><Save size={15} />{saving ? "저장 중" : "확인"}</button></div>
    </form>
  </div>;
}

function DetailModal({ tab, row, onClose }) {
  useEscape(onClose);
  const title = TABS.find(([key]) => key === tab)?.[1] || "상세";
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="modal hardware-detail-modal" role="dialog" aria-modal="true" aria-label={title + " 상세"}>
      <div className="modal-header"><h2>{title} 상세</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <dl className="detail-grid">{COLUMNS[tab].filter(([key]) => tab !== "corporate-phones" || !PHONE_ACCOUNT_FIELDS.some(([field]) => field === key)).map(([key, label]) => <div key={key}>
        <dt>{label}</dt><dd>{display(row[key])}</dd>
      </div>)}</dl>
      {tab === "corporate-phones" && <section className="phone-account-detail" aria-label="계정 정보">
        <h3>계정 정보</h3>
        <dl>{PHONE_ACCOUNT_FIELDS.map(([key, label]) => <div key={key}>
          <dt>{label}</dt><dd>{display(row[key])}</dd>
        </div>)}</dl>
      </section>}
      {tab === "site-accounts" && <section className="site-account-detail" aria-label="계정 정보">
        <h3>계정 정보</h3>
        {row.accounts?.length ? row.accounts.map((entry, index) => <div className="site-account-detail-entry" key={index}>
          <h4>{entry.label}</h4><dl>
            {entry.account && <div><dt>계정</dt><dd>{entry.account}</dd></div>}
            {entry.password && <div><dt>암호</dt><dd>{entry.password}</dd></div>}
            {entry.content && <div className="wide"><dt>내용</dt><dd>{entry.content}</dd></div>}
          </dl>
        </div>) : <p>등록된 계정이 없습니다.</p>}
      </section>}
      <div className="modal-footer">
        <button type="button" className="save" onClick={onClose}>확인</button>
      </div>
    </div>
  </div>;
}

function LicenseGroupDetail({ group, onClose }) {
  useEscape(onClose);
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="modal group-detail-modal" role="dialog" aria-modal="true" aria-label="라이센스 그룹 상세">
      <div className="modal-header"><h2>라이센스 그룹 상세</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <div className="group-summary"><strong>라이센스</strong><span>{display(group.license)}</span><strong>등록 건수</strong><span>{group.rows.length}개</span></div>
      <div className="group-records">{group.rows.map((row) => <section className="group-record" key={row.source_row}>
        <h3>{display(row.name)} <span>#{row.source_row}</span></h3>
        <dl className="detail-grid">{COLUMNS.polaris.filter(([key]) => key !== "license").map(([key, label]) => <div key={key}>
          <dt>{label}</dt><dd>{display(row[key])}</dd>
        </div>)}</dl>
      </section>)}</div>
      <div className="modal-footer"><button type="button" className="save" onClick={onClose}>확인</button></div>
    </div>
  </div>;
}

function LicenseGroupEdit({ group, saving, error, onClose, onSubmit }) {
  useEscape(onClose);
  const submit = (event) => {
    event.preventDefault();
    onSubmit(new FormData(event.currentTarget).get("license"));
  };
  return <div className="modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <form className="modal group-edit-modal" onSubmit={submit} autoComplete="off">
      <div className="modal-header"><h2>라이센스 그룹 수정</h2><button type="button" onClick={onClose} aria-label="닫기"><X size={18} /></button></div>
      <div className="group-edit-body"><p>이 그룹에 속한 {group.count}건의 라이센스 값을 함께 변경합니다.</p>
        <label>라이센스<input name="license" defaultValue={group.license} required maxLength={255} autoFocus autoComplete="new-password" /></label>
        {error && <div className="group-edit-error" role="alert">{error}</div>}
      </div>
      <div className="modal-footer"><button type="button" className="cancel" onClick={onClose}>취소</button>
        <button type="submit" className="save" disabled={saving}><Save size={15} />{saving ? "저장 중" : "수정 저장"}</button></div>
    </form>
  </div>;
}

function App() {
  const [tab, setTab] = useState("google");
  const [rows, setRows] = useState([]);
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState(null);
  const [detail, setDetail] = useState(null);
  const [lockedLicense, setLockedLicense] = useState(null);
  const [groupDetail, setGroupDetail] = useState(null);
  const [groupEditing, setGroupEditing] = useState(null);
  const [groupError, setGroupError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    const tools = document.querySelector(".sticky-tools");
    if (!tools) return;
    const update = () => document.documentElement.style.setProperty("--list-tools-height", `${tools.offsetHeight}px`);
    const observer = new ResizeObserver(update);
    observer.observe(tools);
    update();
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    fetch(API + "/" + tab, { signal: controller.signal, cache: "no-store" })
      .then((response) => response.ok ? response.json() : Promise.reject(new Error("HTTP " + response.status)))
      .then(setRows)
      .catch((failure) => { if (failure.name !== "AbortError") setError("계정 목록을 불러오지 못했습니다."); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [tab]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase("ko");
    const searchable = COLUMNS[tab].filter(([key]) => !SENSITIVE.has(key));
    const matches = needle ? rows.filter((row) => searchable.some(([key]) => display(row[key]).toLocaleLowerCase("ko").includes(needle))) : rows;
    return tab === "polaris" ? [...matches].sort((a, b) => Number(a.license_group) - Number(b.license_group) || Number(a.source_row) - Number(b.source_row)) : matches;
  }, [rows, query, tab]);
  const groupCounts = useMemo(() => {
    const counts = new Map();
    if (tab === "polaris") filtered.forEach((row) => counts.set(row.license_group, (counts.get(row.license_group) || 0) + 1));
    return counts;
  }, [filtered, tab]);

  const changeTab = (next) => {
    setTab(next); setQuery(""); setNotice(""); setEditing(null); setDetail(null); setAdding(false);
    setLockedLicense(null); setGroupDetail(null); setGroupEditing(null); setGroupError("");
  };
  const openRecord = async (row, target) => {
    setNotice("");
    try {
      const response = await fetch(API + "/" + tab + "/" + row.source_row, { cache: "no-store" });
      if (!response.ok) throw new Error();
      const full = await response.json();
      if (target === "edit") setEditing(full); else setDetail(full);
    } catch { setNotice("상세 내용을 불러오지 못했습니다."); }
  };
  const openGroupDetail = async (groupRow) => {
    setNotice("");
    const members = rows.filter((row) => row.license_group_name === groupRow.license_group_name);
    try {
      const fullRows = await Promise.all(members.map(async (row) => {
        const response = await fetch(API + "/polaris/" + row.source_row, { cache: "no-store" });
        if (!response.ok) throw new Error();
        return response.json();
      }));
      setGroupDetail({ license: groupRow.license_group_name, rows: fullRows });
    } catch { setNotice("그룹 상세 내용을 불러오지 못했습니다."); }
  };
  const saveGroup = async (license) => {
    setSaving(true); setGroupError("");
    try {
      const response = await fetch(API + "/polaris/group/" + groupEditing.source_row, {
        method: "PUT", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ license, expectedLicense: groupEditing.license }),
      });
      if (response.status === 409) { setGroupError("같은 라이센스 그룹이 있거나 다른 사용자가 변경했습니다. 목록을 다시 확인해 주세요."); return; }
      if (!response.ok) throw new Error();
      const latest = await fetch(API + "/polaris", { cache: "no-store" });
      if (!latest.ok) throw new Error();
      setRows(await latest.json());
      setGroupEditing(null);
      setNotice("라이센스 그룹을 수정했습니다.");
    } catch { setGroupError("그룹을 수정하지 못했습니다. 다시 시도해 주세요."); }
    finally { setSaving(false); }
  };
  const save = async (values) => {
    setSaving(true); setNotice("");
    try {
      const response = await fetch(API + "/" + tab + (editing ? "/" + editing.source_row : ""), {
        method: editing ? "PUT" : "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(values),
      });
      if (!response.ok) throw new Error("HTTP " + response.status);
      const saved = await response.json();
      if (tab === "polaris") {
        const latest = await fetch(API + "/polaris", { cache: "no-store" });
        if (!latest.ok) throw new Error("목록 새로고침 실패");
        setRows(await latest.json());
      } else {
        setRows((current) => editing
          ? current.map((item) => item.source_row === editing.source_row ? saved : item)
          : [...current, saved].sort((a, b) => a.source_row - b.source_row));
      }
      setAdding(false); setEditing(null); setLockedLicense(null);
      setNotice(editing ? "수정했습니다." : "추가했습니다.");
    } catch { setNotice("저장하지 못했습니다. 입력 내용을 확인해 주세요."); }
    finally { setSaving(false); }
  };
  const remove = async (row) => {
    if (!confirm("해당 항목을 삭제하시겠습니까? 삭제한 데이터는 복구할 수 없습니다.")) return;
    setNotice("");
    try {
      const response = await fetch(API + "/" + tab + "/" + row.source_row, { method: "DELETE" });
      if (!response.ok) throw new Error();
      if (tab === "polaris") {
        const latest = await fetch(API + "/polaris", { cache: "no-store" });
        if (!latest.ok) throw new Error("목록 새로고침 실패");
        setRows(await latest.json());
      } else {
        setRows((current) => current.filter((item) => item.source_row !== row.source_row));
      }
      setNotice("삭제했습니다.");
    } catch { setNotice("삭제하지 못했습니다."); }
  };

  return <main className="app">
    <header className="hero"><div className="hero-title"><h1>계정 리스트</h1></div></header>
    <section className="sticky-tools"><div className="control-row">
      <nav className="tabs" aria-label="계정 목록 종류">{TABS.map(([key, label]) =>
        <button key={key} className={tab === key ? "active" : ""} onClick={() => changeTab(key)}>{label}</button>)}</nav>
      <label className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="검색" aria-label="계정 목록 검색" />
        <button type="button" onClick={() => setQuery("")} aria-label="검색어 지우기">{query && <X size={15} />}</button></label>
      <div className="filter-end"><button className="add-button" onClick={() => { setLockedLicense(null); setAdding(true); }}><Plus size={15} />추가</button></div>
    </div></section>
    {notice && <div className="notice">{notice}</div>}
    {loading && <div className="state">계정 목록을 불러오는 중입니다.</div>}
    {error && <div className="error">{error}</div>}
    {!loading && !error && <section className="table-card" aria-label={TABS.find(([key]) => key === tab)?.[1]}>
      <table className={"table-" + tab}><thead><tr>
        {VISIBLE[tab].map(([key, label]) => <th key={key}>{label}</th>)}
        <th className="manage-heading"><div><span>관리</span><span className="table-count">총 {filtered.length.toLocaleString()}개</span></div></th>
      </tr></thead><tbody>{filtered.map((row, index) => <React.Fragment key={row.source_row}>
        {tab === "polaris" && (index === 0 || row.license_group !== filtered[index - 1].license_group) &&
          <tr className="license-group"><th colSpan={VISIBLE[tab].length}>{display(row.license_group_name)}<span>{groupCounts.get(row.license_group)}개</span></th>
            <th className="group-manage-cell"><div className="actions">
              <button className="detail-button icon-only" title="그룹 자세히" aria-label="라이센스 그룹 자세히" onClick={() => openGroupDetail(row)}><Eye size={14} /></button>
              <button className="edit icon-only" title="그룹 수정" aria-label="라이센스 그룹 수정" onClick={() => { setGroupError(""); setGroupEditing({ source_row: row.source_row, license: row.license_group_name, count: groupCounts.get(row.license_group) }); }}><Pencil size={15} /></button>
              <button className="group-add icon-only" title="그룹에 추가" aria-label="라이센스 그룹에 추가" onClick={() => { setLockedLicense(row.license_group_name); setAdding(true); }}><Plus size={15} /></button>
            </div></th></tr>}
        <tr>
        {VISIBLE[tab].map(([key]) => <td key={key} title={display(row[key])}><span className="cell-text">{display(row[key])}</span></td>)}
        <td className="manage-cell"><div className="actions">
          <button className="detail-button icon-only" title="자세히" aria-label={row.source_row + "번 행 자세히"} onClick={() => openRecord(row, "detail")}><Eye size={14} /></button>
          <button className="edit icon-only" title="수정" aria-label={row.source_row + "번 행 수정"} onClick={() => openRecord(row, "edit")}><Pencil size={15} /></button>
          <button className="danger icon-only" title="삭제" aria-label={row.source_row + "번 행 삭제"} onClick={() => remove(row)}><Trash2 size={15} /></button>
        </div></td>
      </tr></React.Fragment>)}</tbody></table>
      {!filtered.length && <div className="state">검색 결과가 없습니다.</div>}
    </section>}
    {(adding || editing) && <EntryModal key={tab + (editing?.source_row ?? "new")} tab={tab} row={editing}
      lockedLicense={lockedLicense} saving={saving} onClose={() => { setAdding(false); setEditing(null); setLockedLicense(null); }} onSubmit={save} />}
    {detail && <DetailModal tab={tab} row={detail} onClose={() => setDetail(null)} />}
    {groupDetail && <LicenseGroupDetail group={groupDetail} onClose={() => setGroupDetail(null)} />}
    {groupEditing && <LicenseGroupEdit group={groupEditing} saving={saving} error={groupError}
      onClose={() => setGroupEditing(null)} onSubmit={saveGroup} />}
  </main>;
}

createRoot(document.getElementById("root")).render(<App />);
