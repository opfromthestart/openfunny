use jni::JNIEnv;
use jni::sys::jstring;
use jni::objects::{JByteArray, JObject, JString};
use scraper::{Html, Selector};

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_getString(
    env: JNIEnv,
    _obj: JObject,
) -> jstring {
    let mut s = String::from("");

    let c = reqwest::blocking::Client::new();
    let resp = c.get("http://ifunny.co").send().unwrap();
    let html = Html::parse_document(&resp.text().unwrap());
    let sel = Selector::parse("script").unwrap();
    for script in html.select(&sel) {
        let inner = script.inner_html();
        if inner.len() > 24 && &inner[0..24] == "window.__INITIAL_STATE__" {
            let end_pos = inner.find("};").expect("End not found") + 1; // Want to find end of json data
            let json = &inner[25..end_pos];
            // std::fs::write("test.json", json).unwrap()
            let feed_array = &json::parse(json).unwrap()["feed"];
            // println!("{}", feed_array["items"][0]);
            s += &feed_array["items"][0].to_string();
        }
    }

    s = String::from("hi");

    env.new_string(s).unwrap().into_raw()
}

#[no_mangle]
#[allow(non_snake_case)]
pub extern "C" fn Java_com_github_opfromthestart_openfunny_Scraper_getImage<'local>(
    mut env: JNIEnv<'local>,
    _obj: JObject,
    path: JString,
) -> JByteArray<'local> {
    let c = reqwest::blocking::Client::new();
    let resp = c.get(env.get_string(&path).unwrap().to_str().unwrap()).send().unwrap().bytes().unwrap();
    env.byte_array_from_slice(&resp).unwrap()
}