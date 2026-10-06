import test from 'node:test';
import assert from 'node:assert/strict';
import {DOMParser} from '@xmldom/xmldom';
import {zipSync,strToU8} from 'fflate';
import {inspect3mf} from '../src/threeMf.mjs';

globalThis.DOMParser=DOMParser;
const rel='<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="r0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel" Target="/3D/3dmodel.model"/></Relationships>';
const tetra='<object id="1" type="model"><mesh><vertices><vertex x="0" y="0" z="0"/><vertex x="1" y="0" z="0"/><vertex x="0" y="1" z="0"/><vertex x="0" y="0" z="1"/></vertices><triangles><triangle v1="0" v2="2" v3="1"/><triangle v1="0" v2="1" v3="3"/><triangle v1="0" v2="3" v3="2"/><triangle v1="1" v2="2" v3="3"/></triangles></mesh></object>';
const archive=model=>zipSync({'_rels/.rels':strToU8(rel),'3D/3dmodel.model':strToU8(model)}).buffer;
const model=(objects,build,unit='centimeter')=>`<model xmlns="http://schemas.microsoft.com/3dmanufacturing/core/2015/02" unit="${unit}"><resources>${objects}</resources><build>${build}</build></model>`;

test('3MF direct mesh uses declared units and build transform',()=>{
  const result=inspect3mf(archive(model(tetra,'<item objectid="1" transform="1 0 0 0 1 0 0 0 1 2 0 0"/>')));
  assert.equal(result.supported,true);
  assert.equal(result.triangles,4);
  assert.deepEqual(result.dimensions,[10,10,10]);
  assert.deepEqual(result.center,[25,5,5]);
  assert.ok(Math.abs(result.volume-1000/6)<1e-9);
});
test('3MF component assemblies apply nested transforms without claiming a union volume',()=>{
  const assembly='<object id="2" type="model"><components><component objectid="1"/><component objectid="1" transform="1 0 0 0 1 0 0 0 1 2 0 0"/></components></object>';
  const result=inspect3mf(archive(model(tetra+assembly,'<item objectid="2" transform="1 0 0 0 1 0 0 0 1 3 0 0"/>')));
  assert.equal(result.supported,true);
  assert.equal(result.triangles,8);
  assert.deepEqual(result.dimensions,[30,10,10]);
  assert.deepEqual(result.center,[45,5,5]);
  assert.equal(result.volume,null);
});
test('3MF external parts request manual review and malformed packages are rejected',()=>{
  const assembly='<object id="2" type="model"><components><component objectid="1" path="/3D/other.model"/></components></object>';
  const result=inspect3mf(archive(model(tetra+assembly,'<item objectid="2"/>')));
  assert.equal(result.supported,false);
  assert.match(result.reason,/another model part/);
  const cyclic='<object id="2" type="model"><components><component objectid="2"/></components></object>';
  assert.throws(()=>inspect3mf(archive(model(cyclic,'<item objectid="2"/>'))),/cyclic/);
  const prefixed='<object id="2" type="model" xmlns:p="http://schemas.microsoft.com/3dmanufacturing/production/2015/06"><components><component objectid="1" p:path="/3D/other.model"/></components></object>';
  assert.equal(inspect3mf(archive(model(tetra+prefixed,'<item objectid="2"/>'))).supported,false);
  assert.throws(()=>inspect3mf(archive(model(tetra,'<item objectid="99"/>'))),/missing object/);
  assert.throws(()=>inspect3mf(new Uint8Array([80,75,0,0]).buffer));
});
