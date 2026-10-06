export const CAMPAIGN_ACTIONS=['START','EXPLORE','SHOP','MADE'];

export function campaignAction(campaign,button){
  const configured=button==='primary'?campaign.primaryAction:campaign.secondaryAction;
  if(CAMPAIGN_ACTIONS.includes(configured))return configured;
  if(campaign.topic==='3D Printing')return button==='primary'?'START':'EXPLORE';
  return button==='primary'?'EXPLORE':'SHOP';
}
