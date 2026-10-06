import test from 'node:test';
import assert from 'node:assert/strict';
import {projectShareLinks} from '../src/projectShare.mjs';

test('project share links carry the exact published project URL',()=>{
  const url='https://deckers.example/shop/projects/2/4c7d';
  const links=projectShareLinks('Team shirts & signs',url);
  assert.match(decodeURIComponent(new URL(links.whatsapp).searchParams.get('text')),/Team shirts & signs/);
  assert.equal(new URL(links.facebook).searchParams.get('u'),url);
  assert.match(links.message,/make it yours: https:\/\/deckers\.example\/shop\/projects\/2\/4c7d$/);
});
