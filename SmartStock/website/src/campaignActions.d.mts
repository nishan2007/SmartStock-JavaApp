export type CampaignAction='START'|'EXPLORE'|'SHOP'|'MADE';
export declare const CAMPAIGN_ACTIONS:readonly CampaignAction[];
export declare function campaignAction(campaign:{topic:string;primaryAction?:CampaignAction;secondaryAction?:CampaignAction},button:'primary'|'secondary'):CampaignAction;
