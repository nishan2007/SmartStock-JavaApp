import {test} from 'node:test';
import {strict as assert} from 'node:assert';
import {campaignAction} from '../src/campaignActions.mjs';

test('campaign destinations do not depend on editable button copy',()=>{
  const campaign={topic:'3D Printing',primary:'Explore 3D Printing',secondary:'Start a 3D Print'};
  assert.equal(campaignAction(campaign,'primary'),'START');
  assert.equal(campaignAction(campaign,'secondary'),'EXPLORE');
  assert.equal(campaignAction({...campaign,primaryAction:'SHOP',secondaryAction:'MADE'},'primary'),'SHOP');
  assert.equal(campaignAction({...campaign,primaryAction:'SHOP',secondaryAction:'MADE'},'secondary'),'MADE');
});

test('other campaigns retain useful destinations with older catalog settings',()=>{
  const campaign={topic:'Embroidery'};
  assert.equal(campaignAction(campaign,'primary'),'EXPLORE');
  assert.equal(campaignAction(campaign,'secondary'),'SHOP');
});
