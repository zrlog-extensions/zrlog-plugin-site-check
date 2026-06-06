import {FunctionComponent} from "react";
import {SiteCheckInfoResponse} from "./index";
import SiteCheckIndex from "./components/SiteCheckIndex";

export type AppBaseProps = {
    pluginInfo: SiteCheckInfoResponse;
}

const AppBase: FunctionComponent<AppBaseProps> = ({pluginInfo}) => {
    return <SiteCheckIndex data={pluginInfo}/>;
}

export default AppBase;
